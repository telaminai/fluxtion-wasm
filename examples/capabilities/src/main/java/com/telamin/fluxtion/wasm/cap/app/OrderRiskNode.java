package com.telamin.fluxtion.wasm.cap.app;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.callback.EventDispatcher;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;
import com.telamin.fluxtion.wasm.bootstrap.MiniJson;

/**
 * The risk gate of the order desk. Handles a {@code type="order"} event, reads the
 * current position from the injected {@link PositionBook} (browser state), applies a
 * simple limit rule, stores the new position back (browser state), and re-injects a
 * typed {@link Accepted} or {@link Rejected} — which DSL flows map to JSON sinks.
 * Demonstrates a service reading AND storing browser state mid-event, plus typed
 * re-injection keeping the graph standard-shaped.
 */
public class OrderRiskNode {

    /** position limit (abs) beyond which an order is rejected */
    private static final int LIMIT = 1000;

    @Inject
    @NoTriggerReference
    public EventDispatcher eventDispatcher;

    private PositionBook book;

    @ServiceRegistered
    public void wire(PositionBook book, String name) {
        this.book = book;
    }

    @OnEventHandler(filterString = "order")
    public boolean onOrder(StringEvent e) {
        String json = e.payload();
        String symbol = MiniJson.string(json, "symbol");
        String side = MiniJson.string(json, "side");
        int qty = MiniJson.intField(json, "qty", 0);

        if (book == null) {
            eventDispatcher.processReentrantEvent(new Rejected(symbol, qty, "no position service wired"));
            return false;
        }
        int signed = "sell".equals(side) ? -qty : qty;
        int next = book.position(symbol) + signed;

        if (qty <= 0) {
            eventDispatcher.processReentrantEvent(new Rejected(symbol, qty, "qty must be > 0"));
        } else if (Math.abs(next) > LIMIT) {
            eventDispatcher.processReentrantEvent(new Rejected(symbol, qty, "position limit breached: " + next));
        } else {
            book.store(symbol, next);                                   // store browser state
            eventDispatcher.processReentrantEvent(new Accepted(symbol, signed, next));
        }
        return false;
    }
}
