package com.telamin.fluxtion.wasm.cap.reflect;

import com.telamin.fluxtion.wasm.cap.Greeter;
import com.telamin.fluxtion.wasm.cap.PriceStore;
import com.telamin.fluxtion.wasm.cap.app.PositionBook;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.MethodDescriptor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * SPIKE — TeaVM's reflection-metadata route for {@code @ServiceRegistered}.
 *
 * <p>Marks {@code GreeterConsumer.wire(Greeter, String)} reflectable, so that
 * {@code ServiceRegistryNode#scanNode}'s reflection — {@code getMethods()} +
 * {@code getAnnotation()} + {@code Method.invoke()} — can find and call it in WASM.
 * This is the WASM twin of the GraalVM {@code reflect-config} the Fluxtion
 * generator emits for native-image: in production the generator would emit a
 * {@code ReflectionSupplier} like this for every {@code @ServiceRegistered} class.
 *
 * <p>Registered as a build-time SPI via
 * {@code META-INF/services/org.teavm.classlib.ReflectionSupplier}. It runs on the
 * TeaVM build JVM, not inside the wasm.
 */
public class CapReflectionSupplier implements ReflectionSupplier {

    @Override
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
        if ("com.telamin.fluxtion.wasm.cap.GreeterConsumer".equals(className)) {
            // wire(Greeter, String) : void  — the @ServiceRegistered method
            return List.of(new MethodDescriptor("wire", Greeter.class, String.class, void.class));
        }
        if ("com.telamin.fluxtion.wasm.cap.PriceLookupNode".equals(className)) {
            // wirePrices(PriceStore, String) : void — receives the JS-backed service
            return List.of(new MethodDescriptor("wirePrices", PriceStore.class, String.class, void.class));
        }
        if ("com.telamin.fluxtion.wasm.cap.app.OrderRiskNode".equals(className)) {
            // wire(PositionBook, String) : void — receives the JS-backed position store
            return List.of(new MethodDescriptor("wire", PositionBook.class, String.class, void.class));
        }
        return Collections.emptyList();
    }
}
