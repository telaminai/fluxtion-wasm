package com.telamin.fluxtion.wasm.cap.host;

import com.telamin.fluxtion.runtime.service.Service;
import com.telamin.fluxtion.wasm.cap.app.PositionBook;
import com.telamin.fluxtion.wasm.bootstrap.JsonBridgeHost;
import com.telamin.fluxtion.wasm.cap.app.PositionReader;
import com.telamin.fluxtion.wasm.cap.app.PositionWriter;
import com.telamin.fluxtion.wasm.bootstrap.SinkListener;
import com.telamin.fluxtion.wasm.cap.generated.CapabilitiesProcessor;
import org.teavm.jso.JSExport;

/**
 * The export shell for the Live Order Desk app. It reuses the generic
 * {@link JsonBridgeHost} for the whole data path (events in, sinks out, audit) and
 * adds ONE typed method — {@link #wirePositions} — that adapts two JS functions into
 * the {@link PositionBook} service the graph calls. So the app is "the generic bridge
 * + one service registration": the event/sink/audit surface is not app-specific, only
 * the service wiring is.
 */
public class OrderDeskHost {

    private final CapabilitiesProcessor sep;
    private final JsonBridgeHost bridge;

    @JSExport
    public OrderDeskHost() {
        this.sep = new CapabilitiesProcessor();
        this.sep.init();
        this.bridge = new JsonBridgeHost(sep);
    }

    /** Wire the JS-backed position store: reader (read browser state) + writer (store it). */
    @JSExport
    public void wirePositions(PositionReader reader, PositionWriter writer) {
        PositionBook book = new PositionBook() {
            @Override
            public int position(String symbol) {
                return reader.position(symbol);
            }

            @Override
            public void store(String symbol, int position) {
                writer.store(symbol, position);
            }
        };
        sep.registerService(new Service<>(book, PositionBook.class));
    }

    // ── generic bridge surface (delegated, app-agnostic) ──
    @JSExport
    public void onEventJson(String json) {
        bridge.onEventJson(json);
    }

    @JSExport
    public void subscribe(String sinkId, SinkListener listener) {
        bridge.subscribe(sinkId, listener);
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
