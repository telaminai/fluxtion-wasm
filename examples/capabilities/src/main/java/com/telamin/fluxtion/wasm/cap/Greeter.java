package com.telamin.fluxtion.wasm.cap;

/** A service interface injected into a node via {@code @ServiceRegistered}. */
public interface Greeter {

    String greet(String name);
}
