# CLAUDE.md — fluxtion-wasm-capabilities

Bootstrap context for an AI agent (or human) picking up this project. Read this
first, then `docs/SPEC.md` (what + why) and `docs/PHASING.md` (status + next step).

## What this project is

A **single-module** Java/Maven project that answers, with live proof, *"which
Fluxtion capabilities work when a SEP is compiled to WebAssembly?"* — and demos
them in a browser. The web page shows a **capability grid** (capability · ✅/❌ ·
demo) where every ✅/❌ is **self-reported by the demo actually running in WASM**,
never hand-typed. It is a learning reference for newcomers to Fluxtion-on-WASM.

Sibling projects in this repo (`fluxtion-wasm-testharness`):
- `../fluxtion-wasm-conformance` — dual JVM/Node runner that asserts WASM == JVM.
- `../fluxtion-wasm-showcase` — the accept/reject browser demo (the pattern this
  project extends).
- `../docs/fluxtion-wasm-runtime-spec.md` — spec for the eventual npm runtime.
- `../docs/teavm-compatibility-audit.md` — the per-package WASM compatibility audit.

## How to build & run

```bash
# Java 21 is REQUIRED (TeaVM 0.14.1 needs it); node on PATH for the probes.
JAVA_HOME=/path/to/jdk-21 mvn clean install     # JVM tests + TeaVM->wasm + copy into web/
cd web && python3 -m http.server 8000           # open http://localhost:8000 (needs http, not file://)
```

The TeaVM compile is bound to `prepare-package`; `classes.wasm` + the JS loader
are copied into `web/` (and gitignored — never commit the binaries).

## Architecture (one module)

```
src/main/java/com/telamin/fluxtion/wasm/cap/
   CapFuncs, <nodes>                 domain logic + imperative nodes (per capability)
   generated/CapabilitiesProcessor   the generated SEP (COMMITTED; regenerate via gen)
   CapabilitiesHost                  plain-Java host: one probe method per capability
   gen/GenerateCapabilities          manual generator (run by hand, not in the build)
   host/CapHost, host/ExportMain     @JSExport surface over CapabilitiesHost
web/
   fluxtion-wasm-runtime.js          EXTRACTABLE JS library (createProcessor...) — ready for npm
   app.js, index.html                the capabilities grid + demo panels
   classes.wasm, classes.wasm-runtime.js   (copied by the build; gitignored)
```

Why one module (vs the showcase's four): the generated SEP is **committed**, so
there is no gen→sep build cycle; `fluxtion-builder` sits on the classpath only for
the manual generator and TeaVM never reaches it (reachability is from `ExportMain`).

## Proven WASM facts (don't re-derive — these cost real time to establish)

- **1.0.7 is fully closed-world.** DSL operators (map/filter/peek/push/binaryMap/
  groupBy) emit a build-time `MethodReferenceInfo` literal — NO `SerializedLambda`
  at runtime. `LongAdder` is gone (→ `AtomicLong`). Use `fluxtion-runtime` **1.0.7**.
- **Export surface:** `@org.teavm.jso.JSExportClasses({Host.class})` + `@JSExport`
  on the host **constructor + instance methods** → JS does `new teavm.exports.Host()`.
  `int` AND `String` cross the boundary both ways (String proven in the showcase).
  Needs `teavm-jso` (provided). Gotcha: `Object.keys(teavm.exports)` is `[]` —
  exports are reachable by name, not enumerable.
- **runtime.js is NOT emitted by the `compile` goal** — you must also run the
  `copy-webassembly-gc-runtime` goal (both are wired into the pom here).
- **Browser load:** the runtime.js is an IIFE that sets `globalThis.TeaVM`; load it
  via a classic `<script>` (not a module), then `globalThis.TeaVM.wasmGC.load('./classes.wasm')`.
- **Generation:** `Fluxtion.compile(cfg, compilerCfg -> compilerCfg.packageName(..)
  .className(..).outputDirectory(dir).writeSourceToFile(true)
  .copySourceToResourcesDirectory(false).compileSource(false))` writes source only.
- **Egress:** `sep.addSink(id, consumer)` with a plain `Consumer` (no SerializedLambda)
  is WASM-safe. The host captures sink values + exposes them via probes.
- **getNodeById/getStreamed:** the generated `getNodeById` tries a map-based
  `nodeNameLookup` first (WASM-safe) then falls back to `getClass().getField(id)`
  (REFLECTION) for auditors — the reflective branch is a TeaVM risk; PROBE it.

## Capability surface to probe (status lives in docs/PHASING.md)

onEvent · DSL map/filter/peek/push/groupBy · addSink · getStreamed · publishSignal ·
triggerCalculation · audit logging (LogRecordListener) · change log level
(EventLogControlEvent / LogLevel) · exported services (@ExportService) · injected
services (@ServiceRegistered — reflective, likely ❌) · instance callbacks (reflective,
likely ❌). Audit APIs: enable with `config.addEventAudit(LogLevel)`; register a
listener at runtime with `onEvent(new EventLogControlEvent(logRecordListener))`;
`LogRecordListener.processLogRecord(LogRecord)`; `LogRecord.toString()` renders it.

## Standing constraints

- **WASM = the in-memory event processor + DSL only.** Data connectors, event
  feeds, agents, threads, file IO are the host's job — out of scope, will be ❌.
- The grid must be **honest**: a ❌ with the real error is more useful to a newcomer
  than a hidden capability. Self-report, don't assert.
- Never push without explicit approval. Commit messages end with the Co-Authored-By
  line. Don't commit `web/*.wasm` / `web/*.wasm-runtime.js` (gitignored).

## The probe-first workflow (how to extend)

1. Add a node + a `CapabilitiesHost` probe method for the new capability.
2. Add a `@JSExport` method on `CapHost` delegating to it.
3. `mvn install`, then drive the probe in Node (see `docs/PHASING.md` for the runner)
   — record the real ✅/❌ in PHASING.
4. Add a grid row + demo panel in `web/app.js` that calls the probe and self-reports.
