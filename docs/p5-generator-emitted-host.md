# P5a — generator-emitted WASM host (implemented)

**Status:** ✅ **implemented** (2026-06-18) on branch `feature/p5a-generate-wasm-host`
(in both `fluxtion` and `fluxtion-compiler`). P5a of the
[productization spec](fluxtion-wasm-productization-spec.md). Covers the host shell **and**
the `@ServiceRegistered` `ReflectionSupplier`. The "Implemented (as built)" section below
is authoritative; the original design notes follow it for context.

---

## 0. Implemented (as built) — supersedes the design notes where they differ

Two corrections to the original design emerged while building:

1. **The host bundle is the metered deliverable, returned *from the generator*** — not
   emitted locally by the builder. It is rendered **server-side** and carried back in the
   compiler response, so it can be **withheld behind an entitlement gate**. Emitting it
   client-side would give the paid artifact away for free. The gate is at the HTTP
   boundary: `Main.handleGenerateSource` → `isEntitledToWasmHost(req)` (a hook returning
   `true` today; wire it to the real plan/API-key tier).
2. **TeaVM→WASM is a *client-side* compile** (the user runs `teavm-maven-plugin` locally),
   **not** a server stage. The metering is purely "do you get the host bundle," nothing is
   "hosted compile." **This supersedes §4 below** — there is no metered server-side TeaVM
   stage.

**Carrier.** `dto.wasmFiles` (request side `dto.wasmHostSpec`) and
`RemoteGenerationResponse.wasmFiles` — a `Map<name → content>`, mirroring how
`reachabilityMetadataJson` rides the DTO. Bare `*.java` names land in the SEP's source
package; any other key (e.g. `META-INF/services/org.teavm.classlib.ReflectionSupplier`) is
a resources-root-relative path. The client (`EventProcessorGenerator.writeWasmFiles`)
path-routes each entry to disk.

**Bundle contents.** `JsonHost.java` + `JsonHostMain.java` always; plus
`<Sep>ReflectionSupplier.java` + its `META-INF/services` SPI registration **when** the SEP
uses `@ServiceRegistered` — the WASM twin of GraalVM reflect-config, rendered from the same
`ReachabilityMetadataAnalyser` data (a wasm-only build runs the analysis but does **not**
write `reachability-metadata.json`).

**Config.** `FluxtionCompilerConfig.generateWasmHost(boolean)` + `wasmHostClassName`
(default `JsonHost`), opt-in like `generateReachabilityMetadata`.

**Where the code lives.**
- `fluxtion` (open): config flag; `WasmHostSpec` + `wasmFiles` carriers on the DTO/response;
  builder sets the spec, runs the analysis, path-routes + writes the returned files.
- `fluxtion-compiler` (closed): `JsonHostEmitter` + `WasmReflectionSupplierEmitter` render
  the bundle in `SimpleEventProcessorModelGeneratorImpl.generate(dto)`; `Main` gates it.

**Tests.** Emitter units (`WasmEmittersTest`, 5) + end-to-end
(`WasmHostGenerationTest`, 2 — `Fluxtion.compile(...generateWasmHost(true))` writes host +
main + supplier + SPI; nothing when off; no native json on a wasm-only build).

**Cross-repo coupling.** The runtime carriers land in `fluxtion` 1.0.9; the
`fluxtion-compiler` branch pins `1.0.9-SNAPSHOT` until that releases (then → `1.0.9`).

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

## 4. ~~The hosted TeaVM compile (separate, metered)~~ — RETRACTED

> **This section was wrong — see §0.** TeaVM→WASM is a **client-side** compile: the user
> runs `teavm-maven-plugin` (or TeaVM directly) on their own machine. There is **no**
> metered server-side TeaVM stage. The metered thing is the **host bundle in the compiler
> return** (gated at the HTTP boundary, §0) — withholding it is the paywall; the actual
> WASM compile is free and local.

What remains true and useful from the original note:

- The browser side is entirely `@telamin/fluxtion-wasm-runtime` — once the wasm is
  produced, "load wasm, drive it" with no bespoke glue.
- Do NOT conflate with the in-browser CheerpJ DSL *preview* (a different path:
  type-check/preview, not the TeaVM runtime pipeline).

---

## 5. Acceptance

- ✅ `generateWasmHost(true)` emits `JsonHost` of the proven shape (`new JsonBridgeHost`
  delegation; `<Proc> sep = new <Proc>()` the only SEP-specific line) into the SEP package,
  plus its `@JSExportClasses` main — verified by `WasmEmittersTest` + `WasmHostGenerationTest`.
- ✅ `@ServiceRegistered` SEPs get a `ReflectionSupplier` + `META-INF/services` SPI emitted
  alongside, rendered from the reachability analysis.
- ✅ flag off → nothing emitted; wasm-only build → no `reachability-metadata.json`.
- ⏳ **Remaining (follow-up):** migrate the capabilities example to consume the *emitted*
  host (replace the hand-written `host/JsonHost.java`) and confirm the 14 WASM probes pass
  end-to-end through a real TeaVM build. The emitted host targets the SEP package; the
  capabilities app keeps a hand-written `ExportMain` because it bundles app-specific hosts
  (`CapHost`, `OrderDeskHost`) the generator can't know about — exactly the "app-specific
  wiring stays hand-written" caveat in §2.

---

## 6. Honest status

✅ **Implemented** (see §0) on `feature/p5a-generate-wasm-host` in both repos, committed,
unit + e2e tested, **not yet pushed/merged**. Before merge: release `fluxtion` 1.0.9 and
flip the compiler branch's `fluxtion.base.version` `1.0.9-SNAPSHOT → 1.0.9`. The entitlement
gate is a hook (`isEntitledToWasmHost` returns `true`) awaiting the real plan/API-key check.
Follow-ups: the capabilities migration (§5) and wiring the gate.
