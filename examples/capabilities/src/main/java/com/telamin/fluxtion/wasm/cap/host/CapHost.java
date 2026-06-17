package com.telamin.fluxtion.wasm.cap.host;

import com.telamin.fluxtion.wasm.cap.CapabilitiesHost;
import org.teavm.jso.JSExport;

/**
 * WASM export surface — a thin {@code @JSExport} wrapper over {@link CapabilitiesHost}.
 * Each method probes one capability; a returned value = supported, a thrown error
 * = unsupported (the browser/Node caller catches and reports ✅/❌).
 */
public class CapHost {

    private final CapabilitiesHost host = new CapabilitiesHost();

    @JSExport
    public CapHost() {
    }

    @JSExport
    public int dsl(int x) {
        return host.dsl(x);
    }

    @JSExport
    public String getStreamed(int x) {
        return host.getStreamed(x);
    }

    @JSExport
    public String audit(String s) {
        return host.audit(s);
    }

    @JSExport
    public String setLogLevel(String level) {
        return host.setLogLevel(level);
    }

    @JSExport
    public String signal(String value) {
        return host.signal(value);
    }

    @JSExport
    public String exportedService(int a, int b) {
        return host.exportedService(a, b);
    }

    @JSExport
    public String injectedService(String name) {
        return host.injectedService(name);
    }

    @JSExport
    public String callbackViaEvent(String name) {
        return host.callbackViaEvent(name);
    }

    @JSExport
    public String onEventString(String filter, String payload) {
        return host.onEventString(filter, payload);
    }

    // feed a filtered event, read any named sink the graph declares
    @JSExport
    public String eventToSink(String eventString, String filter, String sinkId) {
        return host.eventToSink(eventString, filter, sinkId);
    }

    // edge decoder: json string in → typed Trade re-injected → typed handler downstream
    @JSExport
    public String jsonDecode(String json) {
        return host.jsonDecode(json);
    }

    // a JS function (sym => price) implements the PriceStore service; a node calls it
    @JSExport
    public String priceLookup(JsPriceStore jsPrices, String symbol) {
        return host.priceLookup(jsPrices::priceFor, symbol);   // @JSFunctor → PriceStore
    }

    @JSExport
    public String triggerCalc() {
        return host.triggerCalc();
    }
}
