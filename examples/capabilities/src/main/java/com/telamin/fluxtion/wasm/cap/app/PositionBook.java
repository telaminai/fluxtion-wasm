package com.telamin.fluxtion.wasm.cap.app;

/**
 * The service the order-desk graph uses to read and store running positions. Plain
 * Java — the node knows nothing about JS. In the browser app this is backed by two
 * JS functions (a reader + a writer over a positions table), so the SEP reads and
 * stores BROWSER state while it processes an order.
 */
public interface PositionBook {
    /** Current net position for {@code symbol} (0 if unknown). */
    int position(String symbol);

    /** Store the new net position for {@code symbol}. */
    void store(String symbol, int position);
}
