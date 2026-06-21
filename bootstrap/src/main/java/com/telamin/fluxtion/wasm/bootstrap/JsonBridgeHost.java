package com.telamin.fluxtion.wasm.bootstrap;

import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.service.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The <b>generic JSON value bridge</b>. The reusable layer that lets a JavaScript
 * caller drive ANY Fluxtion SEP with plain JSON — no bespoke {@code @JSExport} verb
 * per app. It holds a {@link DataFlow} (the interface every generated SEP implements),
 * so it is entirely SEP-agnostic: the only per-SEP code is a one-line {@code @JSExport}
 * shell that constructs the SEP and hands it here (the {@code JsonHost} template;
 * later generator-emitted — see {@code package-info}).
 *
 * <p><b>Public API stability:</b> this class is the contract the future
 * generator-emitted host is emitted against. Treat its public methods as a published
 * surface — additive changes only without a major version bump.
 *
 * <p><b>Ingress.</b> {@link #onEventJson} reads a {@code "type"} discriminator and
 * dispatches a generic {@link StringEvent}{@code (type, json)} into the graph. The
 * graph's own decoder nodes (filtered by {@code type}) decode the JSON into typed
 * events and re-inject them — so typing stays in the graph, no reflection, and the
 * graph keeps its standard shape.
 *
 * <p><b>Egress.</b> Sinks are read two ways: {@link #subscribe} pushes each value to
 * a JS callback (drive a DOM element live), and {@link #addSink}/{@link #lastSink}
 * capture the latest value for pull-style reads. Whatever the graph maps to a sink
 * (ideally a JSON string) crosses the boundary as a string.
 *
 * <p><b>Observability.</b> {@link #drainAudit} returns the SEP's audit records, and
 * {@link #setLogLevel} retunes them at runtime — both event-routed, no graph knowledge.
 */
public final class JsonBridgeHost {

    private final DataFlow sep;
    private final Map<String, Object> sinkValues = new HashMap<>();
    private final Set<String> wired = new HashSet<>();
    private final List<String> audit = new ArrayList<>();

    /**
     * @param sep an already-{@code init()}ed SEP (any generated processor is a {@link DataFlow})
     */
    public JsonBridgeHost(DataFlow sep) {
        this.sep = sep;
        // tap the audit stream so the JS side can show log records
        sep.onEvent(new EventLogControlEvent(record -> audit.add(record.toString())));
        // Surface events that reach no @OnEventHandler instead of dropping them silently — the #1
        // "I sent an event and nothing happened" in the tester (e.g. a type with no decoder). Routed
        // into the same audit buffer so drainAudit() / the audit trail shows it.
        sep.setUnKnownEventHandler(e -> audit.add("unhandledEvent: " + e));
    }

    // ── ingress ──────────────────────────────────────────────────────────────

    /** Dispatch a JSON event; {@code "type"} routes it to the graph's decoder for that type. */
    public void onEventJson(String json) {
        String type = MiniJson.string(json, "type");
        sep.onEvent(new StringEvent(type == null ? "" : type, json));
    }

    /** Dispatch a typed/payload event explicitly (no JSON parse). */
    public void onEvent(String type, String payload) {
        sep.onEvent(new StringEvent(type, payload));
    }

    /** Publish a named signal (string value). */
    public void signal(String name, String value) {
        sep.publishSignal(name, value);
    }

    // ── egress ───────────────────────────────────────────────────────────────

    // One real sep.addSink per id (a second call clobbers the first). The consumer
    // captures the JS functor DIRECTLY in its closure — a @JSFunctor stored in a
    // collection and called from elsewhere loses callability in TeaVM. subscribe()
    // registers a capture+push consumer (last subscriber wins, which is all the UI
    // needs); addSink() registers a capture-only consumer iff none exists yet.

    /** Push every value reaching sink {@code sinkId} to a JS callback (live UI). */
    public void subscribe(String sinkId, SinkListener listener) {
        // capture+push in one consumer — also keeps lastSink working. Replaces any
        // prior consumer for this id, but the new one still captures, so pull is intact.
        sep.addSink(sinkId, (Object v) -> {
            sinkValues.put(sinkId, v);
            listener.onValue(String.valueOf(v));
        });
        wired.add(sinkId);
    }

    /** Start capturing the latest value at {@code sinkId} for {@link #lastSink} reads (idempotent). */
    public void addSink(String sinkId) {
        if (wired.add(sinkId)) {
            sep.addSink(sinkId, (Object v) -> sinkValues.put(sinkId, v));
        }
    }

    /** The most recent value at {@code sinkId} as a string, or {@code null} if none yet. */
    public String lastSink(String sinkId) {
        addSink(sinkId);   // tolerate query-before-register: nothing captured yet → null
        Object v = sinkValues.get(sinkId);
        return v == null ? null : String.valueOf(v);
    }

    /** Read the current value of a flow node by id (the {@code .id(...)} tag) as a string. */
    public String getStreamed(String flowId) {
        try {
            Object v = sep.getStreamed(flowId);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    // ── services ─────────────────────────────────────────────────────────────

    /** Register a service implementation (e.g. a JS-backed {@code @JSFunctor}) into the SEP. */
    public void registerService(Service<?> service) {
        sep.registerService(service);
    }

    // ── observability ────────────────────────────────────────────────────────

    /** Return the audit records captured since the last drain, then clear them. */
    public String drainAudit() {
        String s = String.join("\n", audit);
        audit.clear();
        return s;
    }

    /** Retune the audit log level at runtime (TRACE/DEBUG/INFO/WARN/ERROR). */
    public void setLogLevel(String level) {
        sep.onEvent(new EventLogControlEvent(LogLevel.valueOf(level)));
    }
}
