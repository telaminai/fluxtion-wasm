# P5 design — generator-emitted WASM host (and the hosted compile)

**Status:** design only (2026-06-17). P5 of the
[productization spec](fluxtion-wasm-productization-spec.md). Not implemented — the
emit lives in `fluxtion-compiler` (generator-core), changed deliberately, not
overnight. This pins *what* to emit and *where* it hooks so the implementation is
mechanical.

---

## 1. Goal

Remove the **last hand-written Java** a WASM SEP needs: the `@JSExport` host shell.
Today a developer copies the `JsonHost` template (one per SEP, only the processor
class differs). P5 has the generator emit it, so the closed-world promise extends to
the host boundary too: **DSL in → SEP + host + reachability metadata out**, nothing
hand-written.

This is why P1 froze `JsonBridgeHost`'s public API: it is the stable contract the
emitted host is generated against.

---

## 2. What gets emitted

Two tiny files, parameterized only by the **processor FQN**, **package**, and a
**host class name** (default `JsonHost`). Verbatim shape (the proven template — see
`fluxtion-wasm-bootstrap` `package-info` and the working `JsonHost` in
`fluxtion-wasm-capabilities`):

```java
package {pkg};

import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.wasm.bootstrap.JsonBridgeHost;
import com.telamin.fluxtion.wasm.bootstrap.SinkListener;
import {processorFqn};
import org.teavm.jso.JSExport;
import org.teavm.jso.JSExportClasses;

@JSExportClasses({ {hostClass}.class })
public final class {hostClass}Main { public static void main(String[] a) {} }

public final class {hostClass} {
    private final JsonBridgeHost bridge;
    @JSExport public {hostClass}() {
        {processorSimple} sep = new {processorSimple}();
        sep.init();
        bridge = new JsonBridgeHost(sep);   // sep is a DataFlow
    }
    @JSExport public void onEventJson(String json)             { bridge.onEventJson(json); }
    @JSExport public void onEvent(String type, String payload) { bridge.onEvent(type, payload); }
    @JSExport public void addSink(String id)                   { bridge.addSink(id); }
    @JSExport public String lastSink(String id)                { return bridge.lastSink(id); }
    @JSExport public void subscribe(String id, SinkListener l) { bridge.subscribe(id, l); }
    @JSExport public String getStreamed(String id)            { return bridge.getStreamed(id); }
    @JSExport public void signal(String name, String value)    { bridge.signal(name, value); }
    @JSExport public String drainAudit()                       { return bridge.drainAudit(); }
    @JSExport public void setLogLevel(String level)            { bridge.setLogLevel(level); }
}
```

(`@JSExport` must sit on the concrete class — TeaVM does not export inherited methods —
so this is emitted source, not a base class in the lib. That constraint is exactly why
generation, not inheritance, is the answer.)

**App-specific service wiring** (a JS `@JSFunctor` → a Java service) cannot be emitted
generically — it stays a hand-written partial/extension point. Proposal: emit the host
as a non-final class with a protected `wireServices()` hook, or emit only when the SEP
has no `@ServiceRegistered`/`@ExportService` surface and otherwise fall back to the
template + a doc pointer. Decide at implementation.

---

## 3. Where it hooks (fluxtion-compiler)

A generation flag, mirroring the existing reachability-metadata emit
(`setGenerateReachabilityMetadata`):

```java
Fluxtion.compile(cfg -> { ... },
    c -> c.packageName("...").className("MyProcessor")
          .generateWasmHost(true));          // NEW — emit {hostClass} + {hostClass}Main
```

Implementation points:
- **fluxtion-builder** `GenerationContext` / the compiler config: add the
  `generateWasmHost` flag (+ optional `wasmHostClassName`).
- **fluxtion-generator-core** source-writing stage: after the SEP is written, emit the
  two files into the same output package, against the (resolved) processor FQN. Pure
  string templating — no graph analysis needed beyond the processor name.
- **Dependency:** the emitted host needs `fluxtion-wasm-bootstrap` + `teavm-jso` on the
  *consumer's* classpath; the generator only emits source, so document the two deps in
  the generated project's pom (or have the starter add them when the WASM target is on).
- **Reachability twin:** when `generateWasmHost(true)`, also ensure the
  `@ServiceRegistered` classes get a TeaVM `ReflectionSupplier` emitted — the WASM twin
  of the GraalVM reflect-config the generator already produces. This is the natural
  place to close the one reflective seam.

Prototype path (de-risks the compiler change): a standalone `JsonHostEmitter` (plain
string templating, unit-tested against the capabilities `JsonHost` as golden output)
that the generator later calls. Build it first, outside the compiler, then wire it in.

---

## 4. The hosted TeaVM compile (separate, metered)

Distinct from emitting the host: the **playground "WASM project type"** — a server-side
stage that, after the existing cloud generation, runs TeaVM to produce `classes.wasm`
and serves it to the browser. See [`in-browser-compile-spec.md`](in-browser-compile-spec.md).

- TeaVM is a JVM tool (~seconds/SEP) — it **cannot** run in the browser; it's a new
  metered server stage, a pricier tier gated behind generation (fits the
  cloud-compilation revenue model).
- The browser side is entirely `@telamin/fluxtion-wasm-runtime` — once the wasm is
  produced, "load wasm, drive it" with no bespoke glue.
- Do NOT conflate with the in-browser CheerpJ DSL *preview* (a different path:
  type-check/preview, not the TeaVM runtime pipeline).

---

## 5. Acceptance

- `generateWasmHost(true)` on the capabilities SEP emits a `JsonHost` byte-identical
  (modulo the processor name) to the hand-written one; capabilities builds + all WASM
  probes pass with the emitted host.
- A SEP with zero hand-written host Java compiles to a working wasm driven by
  `proc.jsonBridge('JsonHost')`.
- `@ServiceRegistered` SEPs get their `ReflectionSupplier` emitted alongside.

---

## 6. Honest status

Design only. The lib (`fluxtion-wasm-bootstrap`) and its stable API are ready (P1);
the emit is a contained generator-core change best done with the compiler in front of
you, not autonomously. Recommended first step: the standalone `JsonHostEmitter` +
golden test, then the `generateWasmHost` flag.
