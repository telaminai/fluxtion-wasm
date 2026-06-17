package com.telamin.fluxtion.wasm.cap.host;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * JSO view of a JS-provided {@link com.telamin.fluxtion.wasm.cap.PriceStore}. A
 * {@code @JSFunctor} is a single-method interface a plain JS function can implement
 * across the boundary: {@code host.priceLookup(sym => rates[sym], "EURUSD")} passes
 * the JS function as a JsPriceStore, which the host adapts to PriceStore.
 */
@JSFunctor
public interface JsPriceStore extends JSObject {

    int priceFor(String symbol);
}
