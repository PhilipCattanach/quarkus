package io.quarkus.vertx.core.runtime.graal;

import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.ServiceHelper;
import io.vertx.core.Vertx;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.eventbus.impl.HandlerHolder;
import io.vertx.core.eventbus.impl.HandlerRegistration;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.eventbus.impl.OutboundDeliveryContext;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.resolver.DefaultResolverProvider;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.spi.JsonFactory;
import io.vertx.core.spi.json.JsonCodec;
import io.vertx.core.spi.resolver.ResolverProvider;

@TargetClass(className = "io.vertx.core.net.impl.transport.Transport")
final class Target_io_vertx_core_net_impl_transport_Transport {
    @Substitute
    public static Transport nativeTransport() {
        return Transport.JDK;
    }
}

/**
 * This substitution forces the usage of the blocking DNS resolver
 */
@TargetClass(className = "io.vertx.core.spi.resolver.ResolverProvider")
final class TargetResolverProvider {

    @Substitute
    public static ResolverProvider factory(Vertx vertx, AddressResolverOptions options) {
        return new DefaultResolverProvider();
    }
}

@TargetClass(className = "io.vertx.core.net.OpenSSLEngineOptions")
final class Target_io_vertx_core_net_OpenSSLEngineOptions {

    @Substitute
    public static boolean isAvailable() {
        return false;
    }

    @Substitute
    public static boolean isAlpnAvailable() {
        return false;
    }
}

@SuppressWarnings("rawtypes")
@TargetClass(className = "io.vertx.core.eventbus.impl.clustered.ClusteredEventBus")
final class Target_io_vertx_core_eventbus_impl_clustered_ClusteredEventBusClusteredEventBus {

    @Substitute
    private NetServerOptions getServerOptions() {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    public void start(Promise<Void> promise) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    public void close(Promise<Void> promise) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    public MessageImpl createMessage(boolean send, String address, MultiMap headers, Object body, String codecName) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    protected <T> void onLocalRegistration(HandlerHolder<T> handlerHolder, Promise<Void> promise) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    protected <T> HandlerHolder<T> createHandlerHolder(HandlerRegistration<T> registration, boolean replyHandler,
            boolean localOnly, ContextInternal context) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    protected <T> void onLocalUnregistration(HandlerHolder<T> handlerHolder, Promise<Void> completionHandler) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    protected <T> void sendOrPub(OutboundDeliveryContext<T> sendContext) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    protected String generateReplyAddress() {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    protected boolean isMessageLocal(MessageImpl msg) {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    ConcurrentMap connections() {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    VertxInternal vertx() {
        throw new RuntimeException("Not Implemented");
    }

    @Substitute
    EventBusOptions options() {
        throw new RuntimeException("Not Implemented");
    }
}

@TargetClass(className = "io.vertx.core.json.Json", onlyWith = JacksonMissingSelector.class)
final class Target_io_vertx_core_json_Json {

    @Substitute
    public static io.vertx.core.spi.JsonFactory load() {
        io.vertx.core.spi.JsonFactory factory = ServiceHelper.loadFactoryOrNull(io.vertx.core.spi.JsonFactory.class);
        if (factory == null) {
            factory = new JsonFactory() {
                @Override
                public JsonCodec codec() {
                    return null;
                }
            };
        }
        return factory;
    }
}

@TargetClass(className = "io.vertx.core.json.jackson.JacksonCodec", onlyWith = JacksonMissingSelector.class)
@Delete
final class Target_io_vertx_core_json_jackson_JacksonCodec {

}

@TargetClass(className = "io.vertx.core.json.jackson.JacksonFactory", onlyWith = JacksonMissingSelector.class)
@Delete
final class Target_io_vertx_core_json_jackson_JacksonFactory {

}

final class JacksonMissingSelector implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            Class.forName("com.fasterxml.jackson.core.JsonFactory");
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }
}

class VertxSubstitutions {

}
