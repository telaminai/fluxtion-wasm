package com.telamin.fluxtion.wasm.bootstrap;

import com.telamin.fluxtion.runtime.event.Event;

/**
 * The generic wire envelope of the JSON bridge. A {@code filter} (the event
 * {@code type}) routes it — via the SEP's {@code switch(filterString())} dispatch —
 * to the matching decoder node inside the graph, which decodes {@code payload} into
 * the typed domain object and (typically) re-injects it. This is the WASM-friendly
 * ingress for typed events with no per-SEP {@code @JSExport}.
 *
 * <p>Part of the bridge's public contract: {@link JsonBridgeHost} creates it, the
 * app's decoder nodes handle it ({@code @OnEventHandler(filterString = "...")}).
 */
public final class StringEvent implements Event {

    private final String filter;
    private final String payload;

    public StringEvent(String filter, String payload) {
        this.filter = filter;
        this.payload = payload;
    }

    @Override
    public String filterString() {
        return filter;
    }

    public String payload() {
        return payload;
    }
}
