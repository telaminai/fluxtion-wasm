package com.telamin.fluxtion.wasm.cap;

/** A request to price a symbol — fed to the graph, handled by PriceLookupNode. */
public record SymbolEvent(String symbol) {
}
