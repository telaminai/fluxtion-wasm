package com.telamin.fluxtion.wasm.cap;

/**
 * A service the SEP queries — but the IMPLEMENTATION lives in JavaScript. The host
 * adapts a JS function (a {@code @JSFunctor}) to this interface and registers it, so
 * a node can call into the JS context (e.g. a live rate table) during event
 * processing. Plain Java interface — the SEP knows nothing about JS.
 */
public interface PriceStore {

    int priceFor(String symbol);
}
