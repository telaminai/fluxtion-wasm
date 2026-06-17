package com.telamin.fluxtion.wasm.cap.app;

/** A market price tick, re-injected by {@link PriceNode} and emitted to the "marketData" sink. */
public record PriceTick(String symbol, double price) {
}
