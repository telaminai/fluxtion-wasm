package com.telamin.fluxtion.wasm.cap.host;

import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.wasm.bootstrap.JsonBridgeHost;
import com.telamin.fluxtion.wasm.bootstrap.SinkListener;
import com.telamin.fluxtion.wasm.cap.generated.CapabilitiesProcessor;
import org.teavm.jso.JSExport;

/**
 * The WASM export shell for the generic JSON bridge. This is the ONLY per-SEP code
 * the bridge needs: a fixed {@code @JSExport} surface delegating to the reusable
 * {@link JsonBridgeHost}, plus a constructor that names the concrete SEP. Every
 * method here is identical for any SEP — only {@link #initSep()} mentions the
 * processor class, so this shell is a template (later generator-emitted).
 *
 * <p>From JS: {@code const h = proc.newHost('JsonHost'); h.onEventJson('{"type":"json",...}');}
 * — or, more ergonomically, via the runtime package's {@code proc.jsonBridge('JsonHost')}
 * wrapper which marshals plain JS objects for you.
 */
public class JsonHost {

    private final JsonBridgeHost bridge;

    @JSExport
    public JsonHost() {
        this.bridge = new JsonBridgeHost(initSep());
    }

    /** The only SEP-specific line: construct + init the concrete processor. */
    private static DataFlow initSep() {
        CapabilitiesProcessor sep = new CapabilitiesProcessor();
        sep.init();
        return sep;
    }

    @JSExport
    public void onEventJson(String json) {
        bridge.onEventJson(json);
    }

    @JSExport
    public void onEvent(String type, String payload) {
        bridge.onEvent(type, payload);
    }

    @JSExport
    public void addSink(String sinkId) {
        bridge.addSink(sinkId);
    }

    @JSExport
    public String lastSink(String sinkId) {
        return bridge.lastSink(sinkId);
    }

    @JSExport
    public String getStreamed(String flowId) {
        return bridge.getStreamed(flowId);
    }

    @JSExport
    public void subscribe(String sinkId, SinkListener listener) {
        bridge.subscribe(sinkId, listener);
    }

    @JSExport
    public void signal(String name, String value) {
        bridge.signal(name, value);
    }

    @JSExport
    public String drainAudit() {
        return bridge.drainAudit();
    }

    @JSExport
    public void setLogLevel(String level) {
        bridge.setLogLevel(level);
    }
}
