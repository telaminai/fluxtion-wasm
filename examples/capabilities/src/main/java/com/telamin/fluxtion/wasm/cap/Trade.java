package com.telamin.fluxtion.wasm.cap;

/** A strongly-typed domain event. Decoded from JSON at the graph edge and dispatched
 *  into the graph, so downstream nodes handle a typed {@code Trade}, not a string. */
public record Trade(String symbol, int qty) {
}
