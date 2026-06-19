package com.telamin.fluxtion.wasm.cap;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;

import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.service.Service;
import com.telamin.fluxtion.wasm.cap.generated.CapabilitiesProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain-Java host with one probe method per capability. Each probe performs the
 * capability and returns a result string, throwing {@link RuntimeException} on
 * failure — so a caller (the JVM test, a Node runner, or the browser) can record
 * ✅ on a value and ❌ + message on a throw. The wasm module's {@code CapHost} is
 * a thin {@code @JSExport} wrapper over this class.
 *
 * <p>Audit logging works because the runtime {@code EventLogManager} was de-JUL'd in
 * fluxtion 1.0.8 (static Logger + JULLogRecordListener default), so it compiles and
 * runs in WASM.
 */
public final class CapabilitiesHost {

    private final CapabilitiesProcessor sep = new CapabilitiesProcessor();
    private final List<String> auditLines = new ArrayList<>();
    private final Map<String, Object> sinkValues = new HashMap<>();
    private final Set<String> sinkRegistered = new HashSet<>();
    private Integer dslOut;
    private String signalOut;

    public CapabilitiesHost() {
        sep.init();
        // egress: capture the DSL pipeline's sink output
        sep.addSink("dslOut", (Integer v) -> dslOut = v);
        // capture the signal flow's sink output
        sep.addSink("signalOut", (String v) -> signalOut = v);
        // register an audit listener that captures each log record
        sep.onEvent(new EventLogControlEvent(record -> auditLines.add(record.toString())));
    }

    // ── DSL pipeline (map x2 → filter >0 → sink) ──
    public int dsl(int x) {
        dslOut = null;
        sep.onEvent(Integer.valueOf(x));
        if (dslOut == null) {
            throw new RuntimeException("no sink output (filtered)");
        }
        return dslOut;
    }

    // ── getStreamed: read a flow's current value by id (reflective-fallback risk) ──
    public String getStreamed(int x) {
        sep.onEvent(Integer.valueOf(x));
        try {
            Object v = sep.getStreamed("doubled");
            return String.valueOf(v);
        } catch (Exception e) {
            throw new RuntimeException("getStreamed threw " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    // ── audit logging: feed a String, return the captured log records ──
    public String audit(String s) {
        auditLines.clear();
        sep.onEvent(s);
        if (auditLines.isEmpty()) {
            throw new RuntimeException("no audit records captured");
        }
        return String.join("\n", auditLines);
    }

    // ── change log level at runtime (event-based, routed to the auditor) ──
    public String setLogLevel(String level) {
        sep.onEvent(new EventLogControlEvent(LogLevel.valueOf(level)));
        return "log level set to " + level;
    }

    // ── signals: publish a named signal, return what the subscription received ──
    public String signal(String value) {
        signalOut = null;
        sep.publishSignal("greet", value);
        if (signalOut == null) {
            throw new RuntimeException("signal not received at the subscription");
        }
        return signalOut;
    }

    // ── exported service: call the SEP's exported Calculator directly ──
    public String exportedService(int a, int b) {
        Calculator calc = sep.getExportedService(Calculator.class);
        if (calc == null) {
            throw new RuntimeException("getExportedService(Calculator) returned null");
        }
        calc.add(a, b);
        try {
            CalculatorNode node = sep.getNodeById("calc");
            return "add(" + a + "," + b + ") -> total=" + node.getTotal();
        } catch (Exception e) {
            throw new RuntimeException("exported call ok but getNodeById failed: " + e);
        }
    }

    // ── injected service: register a Greeter, see if @ServiceRegistered wired it ──
    public String injectedService(String name) {
        Greeter impl = n -> "hello " + n;
        sep.registerService(new Service<>(impl, Greeter.class));
        try {
            GreeterConsumer node = sep.getNodeById("greeterConsumer");
            String result = node.greetVia(name);
            if ("NOT_INJECTED".equals(result)) {
                throw new RuntimeException("@ServiceRegistered did not inject the service");
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("injected-service probe failed: " + e);
        }
    }

    // ── callback delivered AS AN EVENT, then invoked (the WASM-friendly "service") ──
    public String callbackViaEvent(String name) {
        Greeter impl = n -> "hi " + n;
        sep.onEvent(new ServiceEvent(impl, name));
        try {
            CallbackReceiver node = sep.getNodeById("callbackReceiver");
            String result = node.last();
            if ("NONE".equals(result) || "NULL_CALLBACK".equals(result)) {
                throw new RuntimeException("callback not invoked: " + result);
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("callback-via-event probe failed: " + e);
        }
    }

    // ── generic typed-event ingress: onEventString(filter, payload) routes by filter
    //    to a converter node that decodes the string into a typed result in the graph ──
    public String onEventString(String filter, String payload) {
        sep.onEvent(new StringEvent(filter, payload));
        try {
            StringConverter converter = sep.getNodeById("stringConverter");
            return converter.last();
        } catch (Exception e) {
            throw new RuntimeException("onEventString probe failed: " + e);
        }
    }

    // ── JS-implemented service: register a (JS-backed) PriceStore and query it from
    //    inside the SEP — the node's call reaches back into the JS context ──
    public String priceLookup(PriceStore prices, String symbol) {
        sep.registerService(new Service<>(prices, PriceStore.class));
        sep.onEvent(new SymbolEvent(symbol));
        try {
            PriceLookupNode node = sep.getNodeById("priceLookup");
            int p = node.lastPrice();
            if (p < 0) {
                throw new RuntimeException("price service not injected (reflectable?)");
            }
            return symbol + " = " + p + "  (from the JS price function)";
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("priceLookup probe failed: " + e);
        }
    }

    // ── named sinks: feed a filtered event, return whatever reached the named sink.
    //    Generic — works for ANY sink the graph declares, no per-sink method ──
    public String eventToSink(String eventString, String filter, String sinkId) {
        if (sinkRegistered.add(sinkId)) {
            sep.addSink(sinkId, (Object v) -> sinkValues.put(sinkId, v));   // lazily capture this sink
        }
        sinkValues.remove(sinkId);
        sep.onEvent(new StringEvent(filter, eventString));   // routed by filter to the converter flow
        Object v = sinkValues.get(sinkId);
        if (v == null) {
            throw new RuntimeException("no value reached sink '" + sinkId + "'");
        }
        return String.valueOf(v);
    }

    // ── edge decoder: a json string is decoded by JsonDecoderNode, which re-injects a
    //    typed Trade via the EventDispatcher; TradeHandler (standard typed node) handles it ──
    public String jsonDecode(String json) {
        sep.onEvent(new StringEvent("json", json));   // → decoder → reentrant Trade → typed handler
        try {
            TradeHandler handler = sep.getNodeById("tradeHandler");
            return handler.last();
        } catch (Exception e) {
            throw new RuntimeException("jsonDecode probe failed: " + e);
        }
    }

    // ── manual trigger of the graph ──
    public String triggerCalc() {
        sep.triggerCalculation();
        return "triggerCalculation() returned";
    }
}
