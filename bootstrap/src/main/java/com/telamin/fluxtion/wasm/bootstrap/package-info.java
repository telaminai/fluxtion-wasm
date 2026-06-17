/**
 * <h2>fluxtion-wasm-bootstrap — the reusable Java host layer</h2>
 *
 * Compiled <em>into</em> each TeaVM-compiled Fluxtion SEP wasm. Four classes, all
 * TeaVM-clean (no reflection, no {@code SerializedLambda}, no {@code java.util.logging},
 * no threads/IO):
 *
 * <ul>
 *   <li>{@link com.telamin.fluxtion.wasm.bootstrap.JsonBridgeHost} — the generic JSON
 *       bridge over any {@code DataFlow}; the published API the generator emits against.</li>
 *   <li>{@link com.telamin.fluxtion.wasm.bootstrap.StringEvent} — the wire envelope
 *       ({@code type} + {@code payload}) the graph's decoder nodes handle.</li>
 *   <li>{@link com.telamin.fluxtion.wasm.bootstrap.MiniJson} — a reflection-free flat
 *       JSON field reader.</li>
 *   <li>{@link com.telamin.fluxtion.wasm.bootstrap.SinkListener} — the {@code @JSFunctor}
 *       that turns "a value reached a named sink" into "a JS callback fires".</li>
 * </ul>
 *
 * <h3>The per-SEP {@code JsonHost} shell (template)</h3>
 *
 * The only code a SEP needs to add. {@code @JSExport} must sit on the concrete class
 * (TeaVM does not export inherited methods), so this is a copy-paste template — the
 * only SEP-specific line is the processor class name. A future generator emits exactly
 * this against the (stable) {@link com.telamin.fluxtion.wasm.bootstrap.JsonBridgeHost}
 * API:
 *
 * <pre>{@code
 * @JSExportClasses({ JsonHost.class })
 * public class ExportMain { public static void main(String[] a) {} }
 *
 * public class JsonHost {
 *     private final JsonBridgeHost bridge;
 *     @JSExport public JsonHost() { bridge = new JsonBridgeHost(initSep()); }
 *     private static DataFlow initSep() { var p = new YourProcessor(); p.init(); return p; }
 *
 *     @JSExport public void onEventJson(String json)            { bridge.onEventJson(json); }
 *     @JSExport public void onEvent(String type, String payload){ bridge.onEvent(type, payload); }
 *     @JSExport public void addSink(String id)                  { bridge.addSink(id); }
 *     @JSExport public String lastSink(String id)               { return bridge.lastSink(id); }
 *     @JSExport public void subscribe(String id, SinkListener l){ bridge.subscribe(id, l); }
 *     @JSExport public String getStreamed(String id)            { return bridge.getStreamed(id); }
 *     @JSExport public void signal(String name, String value)   { bridge.signal(name, value); }
 *     @JSExport public String drainAudit()                      { return bridge.drainAudit(); }
 *     @JSExport public void setLogLevel(String level)           { bridge.setLogLevel(level); }
 *     // app-specific service wiring (JS @JSFunctor → Java service) goes here.
 * }
 * }</pre>
 *
 * The graph supplies decoder nodes (handle {@link com.telamin.fluxtion.wasm.bootstrap.StringEvent}
 * by {@code filterString}, re-inject a typed event) and maps typed results to named
 * sinks. The JS side drives it all through {@code @telamin/fluxtion-wasm-runtime}'s
 * {@code proc.jsonBridge('JsonHost')}.
 */
package com.telamin.fluxtion.wasm.bootstrap;
