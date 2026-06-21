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

    /**
     * Readable form for audit logs / debuggers — e.g. {@code StringEvent[number]{"value":21}}.
     * Without this the default {@code Object.toString()} shows an identity hash (and in
     * TeaVM/WASM, the opaque {@code <java_object>@<hash>}), which is meaningless in an audit trail.
     */
    @Override
    public String toString() {
        return "StringEvent[" + filter + "]" + payload;
    }
}
