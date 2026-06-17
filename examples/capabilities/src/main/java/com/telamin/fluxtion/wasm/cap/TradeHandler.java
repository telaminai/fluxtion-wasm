package com.telamin.fluxtion.wasm.cap;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

/**
 * A standard downstream node — it handles a strongly-typed {@link Trade} with a plain
 * {@code @OnEventHandler}, knowing nothing about JSON or strings. The decoding happened
 * once, at the edge ({@link JsonDecoderNode}); the rest of the graph is fully typed.
 */
public class TradeHandler {

    private transient String last = "none";

    @OnEventHandler
    public boolean onTrade(Trade trade) {
        last = "Trade{symbol=" + trade.symbol() + ", qty=" + trade.qty() + "}  (typed, decoded at the edge)";
        return true;
    }

    public String last() {
        return last;
    }
}
