# fluxtion-wasm-bootstrap

The reusable **Java host layer** compiled *into* a TeaVM-compiled Fluxtion SEP. It is
the Java half of the fluxtion-wasm helper libs (the JS half is
[`@telamin/fluxtion-wasm-runtime`](../runtime)).

Four TeaVM-clean classes (no reflection, no `SerializedLambda`, no
`java.util.logging`, no threads/IO):

| Class | Role |
|---|---|
| `JsonBridgeHost` | the **generic JSON bridge** over any `DataFlow` — drive any SEP with plain JSON, no bespoke `@JSExport` verb per app |
| `StringEvent` | the wire envelope (`type` + `payload`) the graph's decoder nodes handle |
| `MiniJson` | a reflection-free flat-JSON field reader |
| `SinkListener` | a `@JSFunctor` so a sink value becomes a JS callback (live UI) |

## Use

```xml
<dependency>
  <groupId>com.telamin.fluxtion</groupId>
  <artifactId>fluxtion-wasm-bootstrap</artifactId>
  <version>0.1.0</version>
</dependency>
```

Add one fixed `@JSExport` shell to your SEP (the only per-SEP code — see the
`package-info` for the copy-paste template):

```java
public class JsonHost {
    private final JsonBridgeHost bridge;
    @JSExport public JsonHost() { bridge = new JsonBridgeHost(initSep()); }
    private static DataFlow initSep() { var p = new YourProcessor(); p.init(); return p; }
    @JSExport public void onEventJson(String json) { bridge.onEventJson(json); }
    // + addSink / lastSink / subscribe / getStreamed / signal / drainAudit / setLogLevel
}
```

The graph supplies decoder nodes (handle `StringEvent` by `filterString`, re-inject a
typed event) and maps typed results to named sinks. JS drives it through
`proc.jsonBridge('JsonHost')` in `@telamin/fluxtion-wasm-runtime`.

## API stability

`JsonBridgeHost`'s public surface is the contract a future generator will emit the
`@JSExport` host against — additive changes only without a major version bump.

## Build

```bash
mvn install   # Java 17+, needs fluxtion-runtime 1.0.8 + teavm-jso 0.14.1 (provided)
```

Apache-2.0. The reference consumer is
[`fluxtion-wasm-capabilities`](../examples/capabilities); the conformance harness
proves JVM == WASM equivalence.
