package com.telamin.fluxtion.wasm.cap.generated;

import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.wasm.bootstrap.JsonBridgeHost;
import com.telamin.fluxtion.wasm.bootstrap.SinkListener;
import org.teavm.jso.JSExport;

/**
 * The WASM export shell for the generic JSON bridge — generated. A fixed {@code @JSExport} surface
 * delegating to the reusable {@link JsonBridgeHost}; only {@link #initSep()} names the concrete
 * SEP. From JS: {@code const h = proc.newHost('JsonHost'); h.onEventJson('{"type":"json",...}');}
 */
public class JsonHost {

  private final JsonBridgeHost bridge;

  @JSExport
  public JsonHost() {
    this.bridge = new JsonBridgeHost(initSep());
  }

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
