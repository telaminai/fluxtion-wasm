package com.telamin.fluxtion.wasm.cap;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;

/**
 * A node that, on a {@link SymbolEvent}, queries an injected {@link PriceStore}.
 * The store is registered at runtime via {@code @ServiceRegistered} (works in WASM
 * with the ReflectionSupplier) — and its implementation is a JS function, so this
 * call reaches back into the JavaScript context from inside the compiled SEP.
 */
public class PriceLookupNode {

    private transient PriceStore prices;
    private transient int lastPrice = -1;

    @ServiceRegistered
    public void wirePrices(PriceStore prices, String name) {
        this.prices = prices;
    }

    @OnEventHandler
    public boolean onSymbol(SymbolEvent e) {
        lastPrice = prices == null ? -1 : prices.priceFor(e.symbol());   // calls into JS
        return true;
    }

    public int lastPrice() {
        return lastPrice;
    }
}
