package io.quarkus.vertx.http.runtime.logstream;

import static java.util.logging.Level.ALL;
import static java.util.logging.Level.CONFIG;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.OFF;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.LogContext;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Stream the log in json format over ws
 */
public class LogStreamWebSocket implements Handler<RoutingContext> {
    private static final Logger log = Logger.getLogger(LogStreamWebSocket.class.getName());
    private final HistoryHandler historyHandler = new HistoryHandler();

    private final JsonObject initMessage = new JsonObject();

    private final ExtHandler rootHandler;
    private final org.jboss.logmanager.Logger rootLogger;

    public LogStreamWebSocket() {

        // Add history handler
        final LogContext logContext = LogContext.getLogContext();
        rootLogger = logContext.getLogger("");
        rootHandler = findCorrectHandler(rootLogger.getHandlers());
        addHandler(historyHandler);

        initMessage.put(TYPE, INIT);
        initMessage.put("loggers", getLoggers());
        initMessage.put("levels", getLevels());
    }

    @Override
    public void handle(RoutingContext event) {
        if ("websocket".equalsIgnoreCase(event.request().getHeader(HttpHeaderNames.UPGRADE))) {
            event.request().toWebSocket(new Handler<AsyncResult<ServerWebSocket>>() {
                @Override
                public void handle(AsyncResult<ServerWebSocket> event) {
                    if (event.succeeded()) {
                        ServerWebSocket socket = event.result();
                        SessionState state = new SessionState();
                        WebSocketHandler webSocketHandler = new WebSocketHandler(socket);
                        state.handler = webSocketHandler;
                        state.session = socket;
                        socket.closeHandler(new Handler<Void>() {
                            @Override
                            public void handle(Void event) {
                                stop(state);
                            }
                        });
                        socket.textMessageHandler(new Handler<String>() {
                            @Override
                            public void handle(String event) {
                                onMessage(event, state);
                            }
                        });
                        socket.writeTextMessage(initMessage.toString());
                        start(state);
                    } else {
                        log.log(Level.SEVERE, "Failed to connect to log server", event.cause());
                    }
                }
            });
        } else {
            event.next();
        }

    }

    static class SessionState {
        ServerWebSocket session;
        ExtHandler handler;
        boolean started;
    }

    public void onMessage(String message, SessionState session) {
        if (message != null && !message.isEmpty()) {
            if (message.equalsIgnoreCase(START)) {
                start(session);
            } else if (message.equalsIgnoreCase(STOP)) {
                stop(session);
            } else if (message.startsWith(UPDATE)) {
                update(message);
            }
        }
    }

    private void start(SessionState session) {
        if (!session.started) {
            session.started = true;
            rootLogger.addHandler(session.handler);

            // Polulate history
            if (historyHandler.hasHistory()) {
                List<ExtLogRecord> history = historyHandler.getHistory();
                for (ExtLogRecord lr : history) {
                    session.handler.publish(lr);
                }
            }
        }
    }

    private void stop(SessionState session) {
        rootLogger.removeHandler(session.handler);
        session.started = false;
    }

    private JsonArray getLevels() {
        return new JsonArray()
                .add(OFF.getName())
                .add(SEVERE.getName())
                .add(WARNING.getName())
                .add(INFO.getName())
                .add(CONFIG.getName())
                .add(FINE.getName())
                .add(FINER.getName())
                .add(FINEST.getName())
                .add(ALL.getName());
    }

    private JsonArray getLoggers() {
        TreeMap<String, JsonObject> loggerMap = new TreeMap<>();
        LogManager manager = LogManager.getLogManager();
        Enumeration<String> loggerNames = manager.getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            JsonObject jsonObject = getLogger(loggerName);
            if (jsonObject != null) {
                loggerMap.put(loggerName, jsonObject);
            }
        }

        List<JsonObject> orderedLoggers = new ArrayList<>(loggerMap.values());
        JsonArray jsonArray = new JsonArray(orderedLoggers);
        return jsonArray;
    }

    private JsonObject getLogger(String loggerName) {
        if (loggerName != null && !loggerName.isEmpty()) {
            Logger logger = Logger.getLogger(loggerName);
            JsonObject jsonObject = new JsonObject();
            jsonObject.put("name", loggerName);
            jsonObject.put("effectiveLevel", getEffectiveLogLevel(logger));
            jsonObject.put("configuredLevel", getConfiguredLogLevel(logger));
            return jsonObject;
        }
        return null;
    }

    private String getConfiguredLogLevel(Logger logger) {
        Level level = logger.getLevel();
        return level != null ? level.getName() : null;
    }

    private String getEffectiveLogLevel(Logger logger) {
        if (logger == null) {
            return null;
        }
        if (logger.getLevel() != null) {
            return logger.getLevel().getName();
        }
        return getEffectiveLogLevel(logger.getParent());
    }

    private void update(String message) {
        String[] p = message.split("\\|");
        if (p.length == 3) {
            String loggerName = p[1];
            String levelVal = p[2];
            Logger logger = Logger.getLogger(loggerName);
            if (logger != null) {
                Level level = Level.parse(levelVal);
                logger.setLevel(level);
            }
        }
    }

    private void addHandler(ExtHandler extHandler) {
        if (rootHandler != null) {
            rootHandler.addHandler(extHandler);
        } else {
            rootLogger.addHandler(extHandler);
        }
    }

    private void removeHandler(ExtHandler extHandler) {
        if (rootHandler != null) {
            rootHandler.removeHandler(extHandler);
        } else {
            rootLogger.removeHandler(extHandler);
        }
    }

    private ExtHandler findCorrectHandler(java.util.logging.Handler[] handlers) {
        for (java.util.logging.Handler h : handlers) {
            if (h instanceof ExtHandler) {
                ExtHandler exth = (ExtHandler) h;
                ExtHandler consoleLogger = getConsoleHandler(exth.getHandlers());
                if (consoleLogger != null) {
                    return exth;
                }
            }
        }
        // Else return first ext handler
        for (java.util.logging.Handler h : handlers) {
            if (h instanceof ExtHandler) {
                return (ExtHandler) h;
            }
        }
        return null;
    }

    private ExtHandler getConsoleHandler(java.util.logging.Handler[] handlers) {
        if (handlers != null && handlers.length > 0) {
            for (java.util.logging.Handler h : handlers) {

                if (h.getClass().equals(org.jboss.logmanager.handlers.ConsoleHandler.class)) {
                    return (ExtHandler) h;
                }
            }
        }
        return null;
    }

    private static final String TYPE = "type";
    private static final String INIT = "init";
    private static final String START = "start";
    private static final String STOP = "stop";
    private static final String UPDATE = "update";

}
