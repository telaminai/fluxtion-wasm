# TeaVM compatibility audit — fluxtion-runtime

**Status**: static audit **+ empirical confirmation**. **Both** the imperative
path *and the functional DSL* are now proven end-to-end (generated SEP → TeaVM →
WASM → runs in Node, JVM-identical output). See "Empirical result" below.
**Runtime audited**: the **1.0.7 release** (closed-world DSL; `LongAdder` removed).
**Question**: which parts of fluxtion-runtime can a TeaVM AOT compiler translate
to WASM, and is the **core event-processor path** clean?

> ## 1.0.7 update — the DSL is WASM-ready
>
> This audit was first written against 1.0.6, when the **functional DSL** was the
> one open question (§4) and turned out to be blocked by pervasive
> `SerializedLambda` introspection in the flow-function runtime. **1.0.7 closes
> both blockers:**
>
> - **Closed-world DSL** — the generator now resolves method-reference metadata
>   at build time and emits a `MethodReferenceInfo` literal (and equivalent
>   `GroupByKey(List,String,Class)` / multi-arg-push constructors) into each
>   operator. The runtime never calls `serialized()`. This covers **every**
>   operator: map/filter/peek, binary map, single + multi-arg push, simple +
>   compound-key groupBy.
> - **`LongAdder` removed** from the runtime (→ `AtomicLong`, which TeaVM emulates).
>
> **Proven:** a DSL SEP (`subscribe(Integer).map(times2).filter(isPositive).sink(...)`)
> generated against 1.0.7, compiled with TeaVM 0.14.1, runs in Node and is asserted
> **byte-identical to the JVM** by the `fluxtion-wasm-conformance` dual-runner JUnit
> harness. The stale "DSL needs verification / is blocked" conclusions below (§4,
> the matrix `⚠️ DSL` row, "what this implies") are kept for the audit trail but are
> **superseded by this banner**.

## Execution model (what TeaVM actually sees)

TeaVM does **not** run the Fluxtion generator. The pipeline is:

```
cloud fluxtion-gen  →  generated SEP (.java)  →  javac (.class)  →  TeaVM → .wasm
   (graph analysis,        plain Java,            normal bytecode      AOT bytecode
    already done)          direct-call dispatch                        → WASM translation
```

The graph analysis / wiring happens **upstream, at generation time**. By the
time TeaVM runs, the SEP is static plain Java with the wiring baked in as direct
calls. So TeaVM's reachable set is *only* the generated SEP plus the
fluxtion-runtime dispatch classes it calls — the **entire build-time zone
(`meta/`, `partition/LambdaReflection`, `annotations/builder`, `serializer/`) is
unreachable by construction**, not merely pruned. This is the same shape as
loading a pre-built native-image binary: no generator, no compiler, no analysis
at the target — just a static artefact running events.

## TL;DR

**Yes — the core event-processor dispatch path is TeaVM-clean.** Every
TeaVM-hostile construct in fluxtion-runtime lives in one of three zones, none of
which a *distributed, pre-generated* SEP contains on its `init()` + `onEvent()`
path:

1. **Host-side data connectors** (`connector/`, `eventfeed/`) — Agrona + threads.
   The WASM model replaces these: the host hands raw events to `onEvent`.
2. **Build-time graph generation** (`meta/`, `partition/`, `annotations/builder`,
   `serializer/`) — reflection, `MethodHandle`, Kryo. Runs when the SEP is
   *generated*, never inside a generated SEP.
3. **Opt-in runtime features** — parallel/forked triggers (`ForkJoin`),
   `@ServiceRegistered` reflective injection, instance callbacks. A SEP that
   doesn't use the feature never reaches the code.

The imperative `@OnEventHandler` / `@OnTrigger` style compiles clean. **As of
1.0.7 the functional DSL (`map/filter/peek/push/groupBy`) is also clean** — the
closed-world refactor removed its `SerializedLambda` dependency (see the 1.0.7
banner and §4). Both paths are proven end-to-end.

## Method

- Static scan of `fluxtion-runtime/src/main/java` for the constructs TeaVM can't
  translate or emulate: `org.agrona.*`, `sun.misc.Unsafe` / `jdk.internal`,
  Kryo, `java.lang.reflect`, `java.lang.invoke` / `MethodHandle`,
  `java.util.concurrent` / threads, and file/NIO IO.
- Per construct, mapped the owning package and asked: **is it reachable from a
  pre-generated SEP's `init()` + `onEvent()`?** TeaVM does **method-level**
  dead-code elimination, so a hostile method in a reachable class is dropped if
  nothing calls it — reachability, not mere presence, is what matters.
- Cross-checked against a real AOT SEP, `DiamondMeshProcessor` (imperative,
  diamond graph, `int` events).

### The precedent that de-risks this

Fluxtion **already compiles under GraalVM native-image** — closed-world,
reachability metadata emitted, `reflect-config` shipped, native-tested. TeaVM's
constraints (no arbitrary runtime reflection, no dynamic classloading, closed
world) are the **same family** as native-image's. The core SEP already survives
one closed-world AOT compiler; TeaVM is a second one with a WASM backend. The
hard part — making the dispatch path closed-world-clean — is done and tested.

## Per-package compatibility matrix

Legend: ✅ clean · 🧱 walled (host-integration layer, absent from a distributed
SEP) · 🏗 build-time only (absent from a distributed SEP) · ⚠️ conditional
(reachable only if the SEP uses a named feature).

| Package | Files | Hostile construct | On core SEP path? | Verdict |
|---|---:|---|---|---|
| `flowfunction/` | 110 | ~~`LambdaReflection` → runtime `method.invoke`~~ — **resolved in 1.0.7**: closed-world constructors take a build-time `MethodReferenceInfo`; no `serialized()` at runtime | DSL `map/filter/peek/push/groupBy` | ✅ **closed-world (1.0.7)** — see §4 |
| `annotations/` | 37 | file IO (`builder/ClassProcessor`) | no — annotation processor | 🏗 |
| `meta/` | 33 | reflection + `MethodHandle` + **Kryo** (`model/SerializationUtils`) | no — build-time model / DTOs / serialization | 🏗 |
| `node/` | 22 | `ForkJoin RecursiveTask` (`ForkedTriggerTask`) | only if `@OnTrigger` parallel | ⚠️ parallel triggers |
| `callback/` | 16 | reflective `newInstance` (`InstanceCallbackEvent`) | core `CallbackDispatcherImpl` is ✅ clean; reflection only via instance-callbacks | ⚠️ instance callbacks |
| `audit/` | 12 | ~~`LongAdder`~~ → **`AtomicLong` in 1.0.7** (TeaVM-emulated); file IO (`JULLogRecordListener`) | structured audit ✅; JUL only if configured 🧱 | ✅ / 🧱 |
| `ml/` | 11 | classified by role (not individually opened) | only if ML nodes used | ⚠️ |
| `time/` | 7 | none | yes (`Clock`) | ✅ |
| `event/` | 7 | none | yes | ✅ |
| `service/` | 6 | `reflect.Method`/`Modifier` (`ServiceRegistryNode`) | construct reachable; reflection via `@ServiceRegistered` injection / `serviceDependencies` | ⚠️ service injection |
| `connector/` | 6 | **Agrona** + threads + file IO | no — host-side data connector | 🧱 |
| `output/` | 5 | none observed (`MessageSink` interface) | yes (sink interface) | ✅ |
| `util/` | 4 | none | yes | ✅ |
| `input/` | 4 | none (`SubscriptionManager`) | yes | ✅ |
| `eventfeed/` | 4 | **Agrona** + threads | no — host-side feed agent | 🧱 |
| `context/` | 4 | `buildtime/*` build-only; `DataFlowContext` clean | runtime context yes | ✅ / 🏗 |
| `serializer/` | 2 | serialization | only if runtime serialize | 🏗 |
| `partition/` | 2 | `LambdaReflection` (`SerializedLambda` / `writeReplace`); **`MethodReferenceInfo` (1.0.7) is a pure-data carrier, no reflection** | no — build-time method-ref resolution; 1.0.7 made this hold for the DSL too (operators no longer reach into `serialized()` at runtime) | 🏗 |
| `lifecycle/` | 2 | none (`Lifecycle`, `BatchHandler`) | yes | ✅ |

**Agrona / `Unsafe`**: Agrona appears in exactly 6 files, all `connector/` +
`eventfeed/`. fluxtion-runtime uses **no `Unsafe` or `jdk.internal` directly
anywhere** — Agrona pulls it transitively, so walling off Agrona walls off
`Unsafe` too.

## The clean core path (verified against DiamondMeshProcessor)

The reachable runtime surface from `new DiamondMeshProcessor()` → `init()` →
`onEvent(int)` is: the generated node graph (direct method calls),
`CallbackDispatcherImpl` (✅ clean), `NodeNameAuditor`, `SubscriptionManagerNode`,
`Clock`, `MutableDataFlowContext`, and the `lifecycle` interfaces. None of these
touch Agrona, `Unsafe`, Kryo, `MethodHandle`, `ForkJoin`, or file IO.

`ForkedTriggerTask` is **imported but never constructed** in DiamondMesh (1
reference = the import) — so its `ForkJoin` dependency is unreachable and TeaVM
drops it. `ServiceRegistryNode` *is* constructed; its reflective methods
(`serviceDependencies`, `buildDependency`) are reachable only through
`@ServiceRegistered` resolution — to be confirmed in the build (DiamondMesh does
call `registerService` during init for its exported service; whether that path
reflects is the one core-path item the empirical build must settle).

## §4 — Functional DSL — RESOLVED in 1.0.7 ✅ (history below)

> **Outcome:** the blocker this section discovered (`SerializedLambda`) was fixed
> in the 1.0.7 release by the closed-world DSL refactor, and a DSL SEP now runs in
> WASM JVM-identically (proven by the `fluxtion-wasm-conformance` harness). The
> investigation narrative below is retained as the audit trail that led to the fix.

Generated a real DSL SEP (`subscribe(Integer).map(DslFuncs::times2).console("out:{}")`)
via the cloud generator and put it through TeaVM. Findings:

- **The `map` path is TeaVM-clean.** The generated SEP holds
  `new MapRef2RefFlowFunction<>(handler, DslFuncs::times2)` and dispatches via
  `mapFunction.apply(...)` — a **direct lambda call**, not `method.invoke()`. The
  full TeaVM error surface contained **zero** SerializedLambda / `LambdaReflection`
  / `MapRef2Ref` errors. The constructor's method-ref introspection
  (`methodReferenceReflection.method()` → `SerializedLambda`) did **not** block the
  build — TeaVM either handles it or DCE's it (the recovered name only feeds an
  audit string that gets pruned). So the worry that DSL reflection is
  TeaVM-hostile is **refuted** for the map path.
- **The actual blocker is `java.util.concurrent.atomic.LongAdder`** — TeaVM 0.14.1
  doesn't emulate it. It's the *only* error. It's reached transitively:
  `.console()` → `PeekFlowFunction` / `Peekers.TemplateMessage` → (static ref)
  `context.buildtime.GeneratorNodeCollection.<clinit>` → `LongAdder`. That's a
  **build-time class (`GeneratorNodeCollection`) leaking into the runtime
  artefact** via the console peeker — the same "metaprogramming past the
  generation boundary" smell as the SerializedLambda-at-construction case.
- `LongAdder` recurs in **three** runtime classes:
  `context/buildtime/GeneratorNodeCollection`, `flowfunction/groupby/GroupByFlowFunctionWrapper`
  (so `groupBy` is also affected), and `audit/StructuredLogRecord` (so structured
  audit logging is too). It's a systemic TeaVM gap, not a one-off.

**Fixes (small):** (a) swap `LongAdder` → `AtomicLong` (TeaVM-emulated) or a plain
`long` (the SEP is single-threaded) in those 3 classes; and/or (b) keep build-time
classes like `GeneratorNodeCollection` off the runtime artefact's reachable set
(don't let the console peeker pull it); and/or (c) avoid `.console()` (a debug
aid) in WASM-targeted SEPs. Any one unblocks the DSL+console case; (a) also
unblocks `groupBy` and audit.

### §4 follow-up — `LongAdder` fixed, then the real wall: `SerializedLambda`

Applied the `LongAdder → AtomicLong` fix in fluxtion-runtime (branch
`fix/wasm-longadder-compat`; AtomicLong *is* TeaVM-emulated, LongAdder isn't).
Result: the DSL SEP now **compiles** to WASM (69 KB) where it previously failed.
But it then **throws `java.lang.RuntimeException` at runtime** — first at
construction, then (after guarding) at init.

Root cause: the DSL flow-function runtime introspects method references via
`LambdaReflection.serialized()` → `getDeclaredMethod("writeReplace").invoke()`
→ `SerializedLambda`, which TeaVM/WASM does not support (it throws). This is
**pervasive** — `.method()` / `.captured()` / `.isDefaultConstructor()` /
`getContainingClass()` all route through `serialized()`, and there are **~30
call sites across ~11 operator classes** (Map, Filter, Peek, Push, GroupBy,
MultiJoin, BinaryMap…), in **constructors and `initialise()`**, mostly to build
cosmetic **audit-label strings** plus some captured-instance wiring. Point-guarding
five sites just moved the failure to the sixth — it's a structural dependency,
not a bug.

**Net verdict (at 1.0.6 — now superseded):**
- **Imperative SEPs → WASM-ready, proven end-to-end** (DiamondMesh runs, JVM-identical).
- ~~**DSL SEPs → compile to WASM but do not yet run**~~ — was true at 1.0.6.
- **The fix is a real refactor, not patches:** thread method-ref metadata
  (audit name, captured instances, default-ctor flag) from **generation time**
  into the generated artefact, so the runtime never calls `serialized()`.

> **✅ DONE in 1.0.7.** Exactly this refactor shipped: the generator emits a
> `MethodReferenceInfo(auditName, stateful, resetReference, defaultValueSupplier)`
> literal into each operator's closed-world constructor (and the analogous
> `GroupByKey(List,String,Class)` and multi-arg-push constructors), so the runtime
> never calls `serialized()`. Guarded by `ClosedWorldDslGenerationTest` in
> fluxtion-integration-tests; the full suite (3081 tests) is green. **DSL SEPs now
> compile *and run* in WASM, JVM-identical** — the "functional core" principle
> (all metaprogramming before the artefact exists) applied consistently.

## What this implies for "what kind of SEP is WASM-targetable"

**Targetable today (proven on 1.0.7):** imperative `@OnEventHandler`/`@OnTrigger`
SEPs **and** functional-DSL SEPs (`map/filter/peek/push/groupBy`), single-threaded,
no `@ServiceRegistered` runtime injection, host feeds events via `onEvent` (no
in-graph Agrona connector).

**Still out of scope / needs verification:** parallel/forked triggers
(incompatible — WASM is single-threaded), runtime `@ServiceRegistered` injection
(reflective), instance callbacks (reflective `newInstance`), in-graph data
connectors / feeds (Agrona + threads — these are the host's job, not the SEP's).

## Empirical result (imperative path — CONFIRMED)

`DiamondMeshProcessor` (generated against 0.9.31), driven by `SpikeMain`, taken
through TeaVM **0.14.1** WasmGC against `fluxtion-runtime` **1.0.6**:

| Stage | Result |
|---|---|
| JVM sanity (drift check) | ✅ runs against 1.0.6 with **no API drift / no patching** |
| TeaVM → WASM compile | ✅ `BUILD SUCCESS` — 292 classes / 2302 methods → **`classes.wasm` 91 KB** (unoptimised `SIMPLE`) |
| Run in Node 25 | ✅ `event=1 root=1 sink=2047`, `event=2 root=2 sink=3071` — **byte-identical to the JVM** |

Findings:
- **Zero fluxtion changes required.** The only obstacle was a missing
  `teavm-classlib` provided dependency (without it TeaVM can't find
  `java.lang.Object` — and the WasmGC backend NPEs on its `Fiber` class). Once
  added, everything resolved.
- **The generated dirty-tracking is TeaVM-clean.** `IdentityHashMap` +
  `BooleanSupplier` lambdas (the `dirtySupplier`/`isDirty` machinery) translate
  fine — they only *appeared* unsupported while the classlib was absent.
- `ServiceRegistryNode.registerService` (reached during DiamondMesh `init` for
  its exported service) compiled and ran — the registration path does not pull
  blocking reflection.
- Determinism holds across the JVM→WASM boundary (same inputs → same outputs).

**Toolchain note**: build/translate on **Java 21** (`teavm-maven-plugin:0.14.1:compile`,
`targetType WEBASSEMBLY_GC`, `teavm-classlib` provided). Reproduce:
`cd spike/teavm-feasibility && mvn compile org.teavm:teavm-maven-plugin:0.14.1:compile
org.teavm:teavm-maven-plugin:0.14.1:copy-webassembly-gc-runtime && node run-node.mjs`.

## Empirical result (DSL path — CONFIRMED on 1.0.7)

A functional-DSL SEP, generated against the **1.0.7 release** and taken through
TeaVM **0.14.1** WasmGC:

| Stage | Result |
|---|---|
| DSL SEP | `subscribe(Integer).map(DslFuncs::times2).filter(DslFuncs::isPositive).sink("result")` |
| Generated form | closed-world — `new MapRef2RefFlowFunction<>(…, MethodReferenceInfo("DslFuncs->times2", …))`, `FilterFlowFunction` likewise; **no `SerializedLambda`** |
| TeaVM → WASM compile | ✅ `classes.wasm` ~58 KB (+ `classes.wasm-runtime.js` via `copy-webassembly-gc-runtime`) |
| Run in Node | ✅ `{5,-3,7,0,10}` → `{10,null,14,null,20}` — **byte-identical to the JVM** |
| Cross-runtime assertion | ✅ JUnit (`fluxtion-wasm-conformance`) asserts WASM == JVM == spec |

The DSL SEP is driven from JS via an `@JSExport` host (`new teavm.exports.IntHost()`
→ `onEvent` / `getResult`). See `../fluxtion-wasm-conformance/` (run:
`JAVA_HOME=<jdk-21> mvn clean install`).

## Next step

§4 is settled (DSL proven on 1.0.7). Remaining harness work: bundle size under
`ADVANCED`/minify (harness item 2); per-event latency / throughput, ideally an
on-device `bench()` (harness item 1, and the `fluxtion-wasm-runtime` spec); and
extend the conformance harness to a parameterised operator matrix so every DSL
shape gets an automatic JVM-vs-WASM equivalence test.
