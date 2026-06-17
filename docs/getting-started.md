# Getting started — a Fluxtion event processor in WebAssembly

Run a Fluxtion SEP (Static Event Processor) in the browser, Node, a web worker, or at
the edge — **deterministic, single-threaded, no server and no AI on the path**, the
same logic as the JVM. This guide takes you from zero to a SEP running in WASM, driven
from JavaScript with plain objects.

> **Status: in development.** The pipeline below is proven (a generated SEP → TeaVM →
> WASM-GC runs JVM-identically; the JSON bridge drives any SEP). The *hosted* in-browser
> compile (a one-click cloud build) is a later stage — this guide is the developer
> pipeline you run yourself.

---

## 1. The two helper libs

You write a Fluxtion graph and a tiny host shell; two libs do the rest:

| Lib | Side | What it gives you |
|---|---|---|
| **`fluxtion-wasm-bootstrap`** (Maven, Apache-2.0) | Java | the generic JSON bridge compiled *into* your SEP wasm — `JsonBridgeHost`, `StringEvent`, `MiniJson`, `SinkListener` |
| **`@telamin/fluxtion-wasm-runtime`** (npm) | JS | loads the wasm and hands you a `JsonBridge` — `onEvent`/`query`/`subscribe`/`signal`/`audit` |

The **reference implementation** of everything here is
[`fluxtion-wasm-capabilities`](../examples/capabilities) — a self-proving capability
grid + a Live Order Desk app. Read its source alongside this guide.

---

## 2. Prerequisites

- **Java 21** (TeaVM 0.14.1 requires it) — e.g. `sdk install java 21.0.5-tem`.
- **A Fluxtion generator** to turn your DSL/graph into a SEP: either the **cloud**
  (a key in `~/.fluxtion`) or the local **`fluxtion-generator-core`** on the classpath.
  *Generation always needs a generator — there is no offline-without-a-generator path.*
- **TeaVM 0.14.1** (`targetType=WEBASSEMBLY_GC`) — a Maven plugin, no install.
- **Node 20+** or a **WASM-GC browser** (recent Chrome/Firefox/Safari) to run it.

---

## 3. The pipeline

```
  your DSL / nodes
        │  generate (cloud key in ~/.fluxtion, or local fluxtion-generator-core)
        ▼
  YourProcessor.java  (the SEP — plain Java, no runtime reflection)
        │  + a JsonHost @JSExport shell (over fluxtion-wasm-bootstrap)
        ▼
  TeaVM compile  (targetType=WEBASSEMBLY_GC, + copy-webassembly-gc-runtime)
        ▼
  classes.wasm + classes.wasm-runtime.js   → copy into web/
        │  load with @telamin/fluxtion-wasm-runtime
        ▼
  const sep = (await createProcessor('./classes.wasm')).jsonBridge('JsonHost');
  sep.onEvent({ type:'trade', ... });   sep.query('positions');
```

Two one-time costs: **generation** (a generator call) and **TeaVM compile** (seconds of
JVM). Everything after is synchronous, on-device dispatch.

---

## 4. Hello SEP — the minimal end-to-end

**a. A decoder node + a sink (the graph).** The decoder is the only thing that knows
the wire format; it re-injects a typed event so the rest of the graph stays typed.

```java
public record Trade(String symbol, int qty) {}

public class TradeDecoder {                                  // edge: knows JSON
    @Inject @NoTriggerReference public EventDispatcher dispatcher;
    @OnEventHandler(filterString = "trade")
    public boolean onTrade(StringEvent e) {                  // StringEvent from the bootstrap lib
        dispatcher.processReentrantEvent(
            new Trade(MiniJson.string(e.payload(), "symbol"),
                      MiniJson.intField(e.payload(), "qty", 0)));
        return false;
    }
}

// a typed flow → a named sink the JS side reads
DataFlowBuilder.subscribe(Trade.class)
        .map(t -> "{\"symbol\":\"" + t.symbol() + "\",\"qty\":" + t.qty() + "}")
        .sink("trades");
```

**b. The host shell** (the only per-SEP `@JSExport`; copy-paste — see the bootstrap
`package-info` for the full template):

```java
@JSExportClasses({ JsonHost.class })
public class ExportMain { public static void main(String[] a) {} }

public class JsonHost {
    private final JsonBridgeHost bridge;
    @JSExport public JsonHost() { bridge = new JsonBridgeHost(initSep()); }
    private static DataFlow initSep() { var p = new YourProcessor(); p.init(); return p; }
    @JSExport public void onEventJson(String j) { bridge.onEventJson(j); }
    @JSExport public void addSink(String id)    { bridge.addSink(id); }
    @JSExport public String lastSink(String id) { return bridge.lastSink(id); }
    @JSExport public void subscribe(String id, SinkListener cb) { bridge.subscribe(id, cb); }
    // + onEvent / getStreamed / signal / drainAudit / setLogLevel
}
```

**c. Drive it from JS:**

```js
import { createProcessor } from '@telamin/fluxtion-wasm-runtime';

const sep = (await createProcessor('./classes.wasm')).jsonBridge('JsonHost');
sep.track('trades');
sep.onEvent({ type: 'trade', symbol: 'EURUSD', qty: 100 });
console.log(sep.query('trades'));   // → { symbol: 'EURUSD', qty: 100 }
```

That's the whole shape: **JSON object in → typed graph → object out.** No bespoke
`@JSExport` per event; the `type` field routes to the matching decoder.

---

## 5. The bridge API (`proc.jsonBridge('JsonHost')`)

| Method | Purpose |
|---|---|
| `onEvent(obj \| json)` | dispatch an event; its `type` routes it to a graph decoder |
| `send(type, payload)` | explicit `(type, payload)` ingress (no JSON parse) |
| `track(id)` / `query(id)` | **pull** the latest value at a named sink (JSON-parsed when JSON) |
| `subscribe(id, cb)` | **push** every sink value to a callback — drive a DOM element |
| `get(id)` | read a flow node's current value by its `.id(...)` tag |
| `signal(name, value)` | publish a named signal |
| `audit()` / `setLogLevel(level)` | drain the audit trail / retune verbosity at runtime |
| `host` | the raw exported host, for app-specific service wiring |

**Services** (a node calls back into JS, or a host→graph typed call): `@ExportService`
(typed handle), `@ServiceRegistered` + a TeaVM `ReflectionSupplier`, a JS-backed
`@JSFunctor`, or callback-via-event. The capabilities grid demonstrates each.

---

## 6. Build config (the pom)

```xml
<dependencies>
  <dependency> fluxtion-runtime          </dependency>   <!-- the SEP runtime -->
  <dependency> fluxtion-wasm-bootstrap    </dependency>   <!-- the bridge, compiled into the wasm -->
  <dependency> teavm-classlib (provided)  </dependency>
  <dependency> teavm-jso      (provided)  </dependency>   <!-- @JSExport / @JSFunctor -->
</dependencies>
```

TeaVM plugin, two goals: `compile` (`targetType=WEBASSEMBLY_GC`, `mainClass=ExportMain`)
and **`copy-webassembly-gc-runtime`** (emits `classes.wasm-runtime.js` — the `compile`
goal does *not*). Then a resources copy of `classes.wasm*` into `web/`. The capabilities
[`pom.xml`](../examples/capabilities/pom.xml) is the working reference, including
the `regen`/`fix-validation` profiles for local generation.

Serve `web/` over **http** (not `file://`): `cd web && python3 -m http.server 8000`.

---

## 7. Deploy targets

Node / browser / web worker / edge (Cloudflare, Fastly). See
[`deployment-targets.md`](deployment-targets.md). The runtime feature-detects WASM-GC
and fails with a clear message on unsupported hosts.

---

## 8. Honest limits

- **Generation needs a generator** (cloud key or local `fluxtion-generator-core`) —
  there is no offline-without-a-generator build.
- **`@ServiceRegistered` is reflective** — it needs a TeaVM `ReflectionSupplier` (the
  WASM twin of GraalVM `reflect-config`, which the generator already emits for
  native-image). Without it the reflective scan silently no-ops; use callback-via-event
  instead.
- **Single-threaded** — no ForkJoin / parallel triggers (WASM has no equivalent).
- **WASM-GC only** — recent browsers / Node 20+; old phones are excluded.
- **Not benchmarked** — we quote no performance numbers. Use the runtime's `bench()` to
  produce honest, on-device throughput for *your* SEP on *your* device.

---

## 9. Next

- Read [`fluxtion-wasm-capabilities`](../examples/capabilities) — the runnable
  capability grid + Live Order Desk, with "How it's built" source tabs.
- The bridge internals + API stability: [`fluxtion-wasm-bootstrap`](../bootstrap).
- The JS host lib: [`@telamin/fluxtion-wasm-runtime`](../runtime).
- Design + boundary contract: [`fluxtion-wasm-runtime-spec.md`](fluxtion-wasm-runtime-spec.md),
  [`fluxtion-wasm-productization-spec.md`](fluxtion-wasm-productization-spec.md).
