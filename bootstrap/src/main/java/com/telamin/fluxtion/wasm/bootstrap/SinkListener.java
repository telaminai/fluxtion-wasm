package com.telamin.fluxtion.wasm.bootstrap;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * A single-method interface a JS function implements so a graph sink can push values
 * back into the JS host. TeaVM maps a plain JS {@code (json) => {...}} function to
 * this {@code @JSFunctor}; the bridge wires it as the sink's {@code Consumer}, so
 * "a value reaching a named sink" becomes "a JS callback fires" — e.g. updating a
 * DOM element. Synchronous, like the rest of SEP dispatch.
 */
@JSFunctor
public interface SinkListener extends JSObject {
    void onValue(String json);
}
