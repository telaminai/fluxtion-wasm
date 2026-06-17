package com.telamin.fluxtion.wasm.cap.app;

/** A risk-accepted order, re-injected by {@link OrderRiskNode} and emitted to the "accepted" sink. */
public record Accepted(String symbol, int qty, int position) {
}
