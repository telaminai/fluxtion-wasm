package com.telamin.fluxtion.wasm.cap.app;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** A JS function {@code (symbol) => position} that reads browser-held position state. */
@JSFunctor
public interface PositionReader extends JSObject {
    int position(String symbol);
}
