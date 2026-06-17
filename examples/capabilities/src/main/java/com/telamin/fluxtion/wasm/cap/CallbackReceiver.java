package com.telamin.fluxtion.wasm.cap;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

/**
 * Receives a callback/service delivered as an event ({@link ServiceEvent}) and
 * invokes it — the non-reflective way to register a service in WASM. Unlike
 * {@code @ServiceRegistered} (which fails in WASM because it relies on reflective
 * method scanning), this is plain {@code @OnEventHandler} dispatch + a direct call.
 */
public class CallbackReceiver {

    private transient String last = "NONE";

    @OnEventHandler
    public boolean onService(ServiceEvent e) {
        last = e.greeter() == null ? "NULL_CALLBACK" : e.greeter().greet(e.name());
        return false;
    }

    public String last() {
        return last;
    }
}
