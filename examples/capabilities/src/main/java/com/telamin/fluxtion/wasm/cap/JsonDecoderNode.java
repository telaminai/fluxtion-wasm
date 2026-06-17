package com.telamin.fluxtion.wasm.cap;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.callback.EventDispatcher;

/**
 * The decoder lives at the graph EDGE: it is the only node that knows about JSON.
 * It handles a {@code filter="json"} {@link StringEvent}, decodes the payload into a
 * strongly-typed {@link Trade}, and RE-INJECTS it into the graph via the
 * {@link EventDispatcher}. Downstream nodes then handle a typed {@code Trade} with a
 * normal {@code @OnEventHandler} — the graph keeps its standard shape, no string
 * handling leaks past the edge.
 *
 * <p>(Alternative to {@code @Inject EventDispatcher}: implement
 * {@code DataFlowContextListener} and grab {@code context.getEventDispatcher()}.)
 */
public class JsonDecoderNode {

    @Inject
    @NoTriggerReference
    public EventDispatcher eventDispatcher;

    @OnEventHandler(filterString = "json")
    public boolean onJson(StringEvent e) {
        String json = e.payload();
        Trade trade = new Trade(field(json, "symbol"), Integer.parseInt(field(json, "qty")));
        eventDispatcher.processReentrantEvent(trade);   // inject the TYPED event mid-cycle
        return false;                                   // the raw string goes no further
    }

    /** Minimal, explicit JSON field read (no reflection) — extract value for "key". */
    private static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        int start = json.indexOf(':', i) + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
            start++;
        }
        int end = start;
        while (end < json.length()
                && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }
}
