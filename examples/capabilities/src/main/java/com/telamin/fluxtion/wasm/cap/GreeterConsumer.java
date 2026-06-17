package com.telamin.fluxtion.wasm.cap;

import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;

/**
 * Injected-service probe. A {@code @ServiceRegistered} method receives a
 * {@link Greeter} the host registers at runtime via {@code registerService(...)}.
 * Whether that injection survives WASM (it is reflective on the JVM) is the
 * capability under test.
 */
public class GreeterConsumer {

    private transient Greeter greeter;

    @ServiceRegistered
    public void wire(Greeter greeter, String name) {
        this.greeter = greeter;
    }

    public String greetVia(String name) {
        return greeter == null ? "NOT_INJECTED" : greeter.greet(name);
    }
}
