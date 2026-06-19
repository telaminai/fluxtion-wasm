package com.telamin.fluxtion.wasm.cap.generated;

import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.MethodDescriptor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Generated — the WASM twin of GraalVM reflect-config. Marks each {@code @ServiceRegistered} method
 * reflectable so {@code ServiceRegistryNode}'s reflection (getMethods + getAnnotation + invoke)
 * resolves it inside the wasm. Registered as a TeaVM build-time SPI via {@code
 * META-INF/services/org.teavm.classlib.ReflectionSupplier}; runs on the TeaVM build JVM, not inside
 * the wasm.
 */
public class CapabilitiesProcessorReflectionSupplier implements ReflectionSupplier {

  @Override
  public Collection<MethodDescriptor> getAccessibleMethods(
      ReflectionContext context, String className) {
    switch (className) {
      case "com.telamin.fluxtion.runtime.input.SubscriptionManagerNode":
        return List.of(
            new MethodDescriptor(
                "registerEventFeedService",
                com.telamin.fluxtion.runtime.input.NamedFeed.class,
                java.lang.String.class,
                void.class),
            new MethodDescriptor(
                "deRegisterEventFeedService",
                com.telamin.fluxtion.runtime.input.NamedFeed.class,
                java.lang.String.class,
                void.class));
      case "com.telamin.fluxtion.runtime.output.SinkPublisher":
        return List.of(
            new MethodDescriptor(
                "messageSinkRegistered",
                com.telamin.fluxtion.runtime.output.MessageSink.class,
                java.lang.String.class,
                void.class),
            new MethodDescriptor(
                "messageSinkDeregistered",
                com.telamin.fluxtion.runtime.output.MessageSink.class,
                java.lang.String.class,
                void.class));
      case "com.telamin.fluxtion.wasm.cap.GreeterConsumer":
        return List.of(
            new MethodDescriptor(
                "wire",
                com.telamin.fluxtion.wasm.cap.Greeter.class,
                java.lang.String.class,
                void.class));
      case "com.telamin.fluxtion.wasm.cap.PriceLookupNode":
        return List.of(
            new MethodDescriptor(
                "wirePrices",
                com.telamin.fluxtion.wasm.cap.PriceStore.class,
                java.lang.String.class,
                void.class));
      case "com.telamin.fluxtion.wasm.cap.app.OrderRiskNode":
        return List.of(
            new MethodDescriptor(
                "wire",
                com.telamin.fluxtion.wasm.cap.app.PositionBook.class,
                java.lang.String.class,
                void.class));
      default:
        return Collections.emptyList();
    }
  }
}
