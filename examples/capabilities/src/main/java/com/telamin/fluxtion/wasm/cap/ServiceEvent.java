package com.telamin.fluxtion.wasm.cap;

/**
 * Carries a service/callback ({@link Greeter}) into the graph as an ordinary
 * event — the WASM-compatible alternative to reflective {@code @ServiceRegistered}
 * injection. A node handles it via a normal {@code @OnEventHandler} (instanceof
 * dispatch, which works in WASM) and stores/invokes the callback.
 */
public record ServiceEvent(Greeter greeter, String name) {
}
