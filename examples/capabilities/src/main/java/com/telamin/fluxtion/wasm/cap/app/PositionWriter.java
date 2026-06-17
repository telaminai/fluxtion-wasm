package com.telamin.fluxtion.wasm.cap.app;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** A JS function {@code (symbol, position) => {}} that stores browser-held position state. */
@JSFunctor
public interface PositionWriter extends JSObject {
    void store(String symbol, int position);
}
