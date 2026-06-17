package com.telamin.fluxtion.wasm.cap.app;

/** A risk-rejected order with a reason, emitted to the "rejected" sink. */
public record Rejected(String symbol, int qty, String reason) {
}
