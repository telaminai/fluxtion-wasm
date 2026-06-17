# PHASING — fluxtion-wasm-capabilities

Status tracker. Update the tables as work lands. Date format: absolute.

## Phases

| Phase | Goal | Status |
|---|---|---|
| 0 | Scaffold — single-module pom, docs, CLAUDE.md | ✅ done |
| 1 | **Probe pass** — drive each capability through WASM, record real ✅/❌ | ✅ done — 14 capabilities probed, all ✅ incl. injected-service (`@ServiceRegistered` via a TeaVM ReflectionSupplier), JS-implemented service (`@JSFunctor`), the generic JSON bridge + edge-decoder re-injection, and the Live Order Desk app. Only host-side connectors / ForkJoin are ❌ by design (single-threaded) |
| 2 | Web grid + per-capability demo panels (self-reporting) | ✅ done — `web/index.html` + `app.js`; each row runs live in WASM, validated via DOM sim |
| 3 | Extract `web/fluxtion-wasm-runtime.js` into a clean, npm-ready library | 🟡 in place as a standalone lib (`createProcessor`/`FluxtionProcessor`); not yet published to npm |
| 4 | Polish + host (GitHub Pages / marketing site) | ⬜ not started |

## Capability probe results (the source of truth for the grid)

Fill `Result` from an actual WASM run (Node or browser), with the error text on ❌.

| Capability | Probe method | JVM | WASM | Notes |
|---|---|---|---|---|
| onEvent + DSL map/filter → sink | `dsl(int)` | ✅ | ✅ | `dsl(5)`→10 both runtimes |
| getStreamed (flow by id) | `getStreamed(id)` | ✅ | ✅ | `getStreamed(7)`→14. Reflective fallback in getNodeById did NOT break TeaVM — the map-based `nodeNameLookup` path wins for a real node |
| triggerCalculation | `triggerCalc()` | ✅ | ✅ | |
| **Audit logging** | `audit(String)` | ✅ | ✅ **(fixed in 1.0.8)** | The runtime `EventLogManager` / `cfg.addEventAudit(...)` was ❌ on ≤1.0.7 (pulled `java.util.logging` + `instanceof ForkedTriggerTask`). **De-JUL'd in fluxtion 1.0.8** (static Logger removed, default ctor → `System.out::println`, ForkedTriggerTask branch removed) → now compiles + runs in WASM: `audit("claim-42")` returns the captured `eventLogRecord:…`. Validated here via `-Pregen` (real `addEventAudit`). The `WasmEventLogManager` shim is retired to the `-Pshim-audit` profile (only for building against released 1.0.7). |
| **Change log level** | `setLogLevel(String)` | ✅ | ✅ | `EventLogControlEvent(LogLevel)` routed to the runtime `EventLogManager` — works now that audit is de-JUL'd. |
| publishSignal + signal handler | `signal(...)` | ✅ | ✅ | `subscribeToSignal("greet",String) → sink`; `publishSignal("greet","world")`→"world" |
| Exported service (@ExportService) | `exportedService(...)` | ✅ | ✅ | `getExportedService(Calculator).add(3,4)`; effect read via getNodeById. NOTE: exported methods are **void/boolean dispatch only** — a value-returning method (`int total()`) is generated as `void` and won't compile. |
| **Injected service (@ServiceRegistered)** | `injectedService(...)` | ✅ | ✅ **(with a ReflectionSupplier)** | `ServiceRegistryNode` wires services by **reflection** — `getMethods()` + `@ServiceRegistered` scan + `Method.invoke()` (ServiceRegistryNode.java:271-275,100). By DEFAULT WASM has no metadata for it → silent no-op (❌). **But TeaVM has its own reflection-metadata route**: register the `@ServiceRegistered` class via a `org.teavm.classlib.ReflectionSupplier` (ServiceLoader SPI) and the full chain (getMethods + getAnnotation + invoke) runs in WASM — **validated: `injectedService` → "hello world"** (`src/.../reflect/CapReflectionSupplier.java`). This is the WASM twin of GraalVM reflect-config. (Or avoid reflection entirely via callback-via-event / M4 static wiring.) NOT an instanceof problem — event dispatch is instanceof and always worked. |
| **Service/callback delivered as an EVENT** | `callbackViaEvent(...)` | ✅ | ✅ | The WASM-compatible alternative to `@ServiceRegistered`: a `ServiceEvent` carries a `Greeter`; a plain `@OnEventHandler` captures + invokes it → "hi world". Proves callbacks *do* work in WASM — only the reflective *registry* doesn't. |
| **JS-implemented service** | `priceLookup(jsFn, sym)` | n/a | ✅ | A JS function implements a Java service via TeaVM **`@JSFunctor`** (`JsPriceStore`); the host adapts it (`js::priceFor` → `PriceStore`) and registers it; a `@ServiceRegistered` node calls it during processing → **calls back into JS** (`priceLookup(sym=>rates[sym],"EURUSD")` → "EURUSD = 108"). Proves the **bidirectional boundary** — JS→WASM (the fn in) + WASM→JS (the callback out) — so the SEP can query live JS state. Synchronous only (async JS → model as events). Needs the ReflectionSupplier (for the injection) + teavm-jso. |
| Instance callbacks | tbd | ⬜ | ⬜ | reflective newInstance — likely ❌ |
| buffer / window | tbd | ⬜ | ⬜ | unknown — may pull timers (deferred) |

Legend: ✅ works · ❌ fails (record error) · ⚠️ partial · ⬜ not yet probed.

### Key finding (2026-06-16): audit logging — FIXED in fluxtion 1.0.8

On ≤1.0.7 the runtime `EventLogManager` (what `cfg.addEventAudit(...)` uses) was ❌
for WASM: a static `java.util.logging.Logger` + default `JULLogRecordListener` (JUL)
**and** an `instanceof ForkedTriggerTask` (ForkJoin) — TeaVM compiles none of them.
The interim workaround was a JUL-free reimplementation (`WasmEventLogManager`,
registered via `addAuditor`).

**The proper fix landed in fluxtion 1.0.8** (the three sources removed/replaced:
static Logger gone, default ctor `new JULLogRecordListener()` → `this(System.out::println)`,
ForkedTriggerTask branch removed). Validated end-to-end through this harness
(`-Pregen` regenerates with `cfg.addEventAudit(...)`; the WASM build now compiles and
`audit(...)` returns records). So **the project now uses the real `EventLogManager`
by default** (requires fluxtion ≥ 1.0.8); the `WasmEventLogManager` shim is retired
to the `-Pshim-audit` profile (only for building against released 1.0.7).

### Key finding (2026-06-16): @ServiceRegistered fails because of `ServiceRegistryNode#scanNode`

`ServiceRegistryNode.scanNode` (ServiceRegistryNode.java:269) wires services to
nodes purely by reflection: `clazz.getMethods()` → read `@ServiceRegistered` /
`@ServiceDeregistered` → wrap each `Method` in a `Callback` → `Method.invoke()` on
registration. TeaVM has no `getMethods()` metadata, so the scan finds nothing and
registration silently no-ops. This is the *only* reason injected services fail —
event/type dispatch (instanceof) works fine.

**Fix = full AOT (there is a runtime spec for this):** resolve the
service→`@ServiceRegistered` bindings at **generation time** (the builder scans on
the JVM, where reflection works) and emit a generated registration table with
**direct** callbacks (`node::wire`) instead of reflective `Method.invoke`. Same
"metaprogramming before the artefact exists" principle as the closed-world DSL
(`MethodReferenceInfo`). **Double win:** (1) `@ServiceRegistered` works in WASM,
and (2) the GraalVM native-image **reflect-config for service methods can be
dropped** — native-image needed it only because `scanNode` reflects at runtime.
WASM-today workaround: deliver the service as an event (the `callbackViaEvent`
row — ✅).

## How to run a probe (Node)

After `mvn install` (which builds `target/wasm-gc/classes.wasm` + runtime.js):

```js
// drive a probe method on the exported CapHost
import { pathToFileURL } from 'node:url';
const dir = 'target/wasm-gc';
await import(pathToFileURL(`${dir}/classes.wasm-runtime.js`).href);
const teavm = await globalThis.TeaVM.wasmGC.load(`${dir}/classes.wasm`);
const host = new teavm.exports.CapHost();
console.log(host.audit('hello'));   // try a capability; a throw = ❌
```

A reusable probe runner script lives at `probe/run-probes.mjs` (add it in Phase 1)
and prints a ✅/❌ table that mirrors the one above.

## Decisions / open questions

- Grid source = **self-reporting in-browser** (each demo runs live, catches failure).
  A build-time matrix could be added later as a second source of truth.
- One SEP for all probes vs several — start with one; split only if a capability
  needs an incompatible graph shape.
- Audit logging is the priority probe (the user has hit it). If it fails in WASM,
  capture exactly where (listener registration vs record emission vs System.out).

## Changelog

- (Phase 0) scaffold: pom (single module, builder+runtime+teavm+jso+junit, TeaVM
  compile + copy-webassembly-gc-runtime + copy-to-web), CLAUDE.md, SPEC.md, this file.
