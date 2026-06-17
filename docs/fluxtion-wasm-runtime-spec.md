# Spec — `fluxtion-wasm-runtime`

**Status:** v0.1.0 SHIPPED (2026-06-16) — the host surface is built and consumed
(see §14). The Tier-1 JSON event bridge and Tier-2 typed adapter remain spec.
Original status line below kept for context.

**Status (original):** draft spec for review. No code yet.
**Author context:** written after the 1.0.7 baseline was proven — a closed-world
DSL SEP compiles to WASM-GC (TeaVM 0.14.1) and runs in Node with JVM-identical
output. See `pipeline.md`, `deployment-targets.md`, `node-developer-experience.md`.

---

## 1. Purpose

`fluxtion-wasm-runtime` is the **host-side runtime** that turns a TeaVM-compiled
Fluxtion SEP (`classes.wasm`) into a usable event processor in any JavaScript
environment — Node, browser, web worker, Cloudflare/Fastly edge, and phone
browsers.

It is the concrete implementation of the facade the other docs assume:

```js
import { createProcessor } from '@telamin/fluxtion-wasm-runtime';
const sep = await createProcessor(wasmUrl);
sep.onEvent({ kind: 'trade', symbol: 'EURUSD', qty: 100 });
const pos = sep.query('positionFor', 'EURUSD');
```

Today that facade does not exist — the proven spike only calls the WASM module's
`main()`. This project is the reusable layer between "a `.wasm` file" and "an app".

**One-line scope:** *all the bindings, marshalling, lifecycle and tooling needed
to run a Fluxtion SEP in a WASM host — and nothing Fluxtion-graph-specific (that
is compiled into the SEP wasm itself).*

---

## 2. Why it's a separate top-level project

- It is **language-split**: a JS/TS npm package plus a small Java bootstrap that
  is compiled *into* each SEP wasm. Neither belongs in `fluxtion-runtime`
  (pure Java) nor in the generator.
- It has its **own release cadence and consumers** (Node/edge developers who
  never touch the JVM — ICP 2 / ICP 3 in `node-developer-experience.md`).
- It is the **shared dependency** of every WASM consumer: the Node example, the
  browser showcase, the playground "TeaVM project type", and the phone demo all
  load the *same* runtime.

---

## 3. The two halves

```
   ┌─────────────────────────── one SEP build ───────────────────────────┐
   │                                                                      │
   │   Fluxtion DSL  ──gen──►  YourProcessor.java  ──┐                     │
   │                                                 │ TeaVM compile      │
   │   fluxtion-wasm-bootstrap (Java)  ──────────────┤  (WASM-GC)         │
   │     · exported entry points                     ▼                    │
   │     · event/result marshalling          classes.wasm  + runtime.js   │
   └──────────────────────────────────────────────────────┬──────────────┘
                                                           │ loaded by
   ┌───────────────────────────────────────────────────────▼─────────────┐
   │   @telamin/fluxtion-wasm-runtime  (JS / TS, npm)                      │
   │     · createProcessor(wasmUrl)        · onEvent / query / subscribe   │
   │     · environment detection           · audit + console tap          │
   │     · lifecycle (init / tearDown)      · benchmark harness            │
   └──────────────────────────────────────────────────────────────────────┘
                                                           │ used by
                      Node app · browser page · edge worker · playground · phone
```

### 3a. `fluxtion-wasm-bootstrap` (Java, compiled into every SEP wasm)

The Java-side counterpart. Provides the **exported static entry points** TeaVM
exposes to JS, wrapping the generated SEP:

- `init()` — construct the SEP, call `init()`.
- `onEvent(...)` — accept an event across the boundary, dispatch to the SEP.
- `query(name, arg)` — read a named sink / `getStreamed(id)` value.
- `drainAudit()` / console tap — surface the SEP's audit + log output.

This is where the **boundary marshalling** lives (§5). It must be TeaVM-clean
(no reflection, no `SerializedLambda`, no threads, no file IO).

> It may eventually be **generated** per-SEP (typed entry points matching the
> SEP's event classes) rather than hand-written generic. See §5.

### 3b. `@telamin/fluxtion-wasm-runtime` (JS / TS, npm)

The host bindings. Wraps TeaVM's loader, exposes the facade, and adds the
ergonomics TeaVM does not: environment detection, a clean lifecycle, result
subscription, audit routing, and the benchmark harness.

---

## 4. JS/TS API surface (v1)

```ts
// load + lifecycle
function createProcessor(wasm: string | URL | ArrayBuffer,
                         opts?: ProcessorOptions): Promise<Processor>;

interface Processor {
  onEvent(event: unknown): void;            // ingress (see §5 boundary)
  query<T = unknown>(name: string, arg?: unknown): T;   // egress: named sink / getStreamed
  subscribe(sink: string, cb: (value: unknown) => void): () => void;  // push egress
  onAudit(cb: (entry: AuditEntry) => void): () => void; // observability tap
  reset(): void;                            // SEP reset where supported
  tearDown(): void;                         // release wasm instance

  // benchmark harness (§7)
  bench(events: Iterable<unknown>, opts?: BenchOptions): BenchResult;
}

interface ProcessorOptions {
  env?: 'auto' | 'node' | 'browser' | 'worker';
  deobfuscate?: boolean;       // load the optional stack-deobfuscator wasm
  audit?: boolean;             // wire the audit tap
}

interface BenchResult {
  events: number;
  millis: number;
  eventsPerSecond: number;
  p50Nanos?: number; p99Nanos?: number;   // when per-event timing is enabled
  device: string;             // navigator.userAgent / process info — measured, not claimed
}
```

Design rules:

- **Measured, never asserted.** `bench()` reports what *this device* did. We do
  not bake performance numbers into the runtime or the docs (native/WASM is not
  benchmarked — the harness is how numbers get produced honestly, per device).
- **Synchronous `onEvent`.** The SEP is a synchronous dispatch engine; the only
  async is the one-time `createProcessor` load.
- **No graph knowledge.** The runtime never knows operator types; it only knows
  the four boundary verbs (init / onEvent / query / drainAudit).

---

## 5. The boundary contract (the hard part)

The central design problem is **how a JS value becomes a Java event** (and back)
across the TeaVM WASM-GC boundary. Primitives and strings cross cheaply; arbitrary
Java objects need GC interop. Proposed two tiers:

### Tier 1 — value/JSON bridge (v1, proven-path)

The bootstrap exports string/primitive-typed functions:

```
onEventJson(json: string): void
query(name: string, argJson: string): string
```

JS serialises the event to JSON; a thin Java adapter in the wasm deserialises to
the event object, dispatches, and serialises results back. Works **today** with
the export surface we already have (strings cross WASM-GC fine).

- ✅ Simple, language-agnostic, ships now, fine for playground/demos.
- ⚠️ Per-event allocation + parse cost — **must be measured** before quoting it
  as the perf story. The phone "performance demo" should use Tier 2 or a
  primitive fast-path, not the JSON bridge, or it measures JSON not Fluxtion.

### Tier 2 — typed / fast-path adapter (v2, perf)

For hot paths and the perf demo, a **generated per-SEP adapter** exports typed
entry points matching the SEP's event classes (e.g. `onTrade(symbolPtr, qty)`),
using TeaVM's object/array interop to avoid per-event JSON. Couples to the
generator (it knows the event types), so it's a generation-time emit, mirroring
how the closed-world ctors are emitted.

- ✅ No per-event serialisation; suitable for throughput benchmarks.
- ⚠️ Requires validating the **WASM-GC export + object-interop surface** in
  TeaVM 0.14.1 (open item §9). The Node reference example (task) is the prototype
  that pins this down.

**Recommendation:** ship Tier 1 first (unblocks playground + Node example +
browser showcase), prototype Tier 2 alongside the perf demo.

---

## 6. Host environments

One package, capability-detected:

| Env | Load mechanism | Notes |
|---|---|---|
| Node 18+ | `fs` + `TeaVM.wasmGC.load` | the default; what the spike runs on |
| Browser | `fetch` + `WebAssembly` | needs **WASM-GC** support (modern Chrome/Firefox; recent Safari/iOS — detect + graceful message on old phones) |
| Web worker | same as browser, off main thread | keeps the UI responsive during a bench loop |
| CF Workers / Fastly | bundled bytes | edge target; see `deployment-targets.md` |

The runtime must **feature-detect WASM-GC** and fail with a clear message rather
than a cryptic instantiate error on an unsupported (older phone) browser.

---

## 7. Benchmark harness — and the phone demo

This is what answers *"can we run a performance-style demo on a phone?"*

**Yes.** The shape:

1. Server compiles the SEP to wasm (the playground "TeaVM project type" — an
   extra server-side compile step after the existing cloud generation; TeaVM is
   a JVM tool, ~seconds per SEP, so it **cannot** run in the browser).
2. The phone browser loads the wasm via `fluxtion-wasm-runtime`.
3. `bench()` runs a tight `onEvent` loop on-device and reports `eventsPerSecond`
   + percentiles for **that phone**.

Why this is a strong, honest story: the number is **measured live on the user's
own device**, not a figure we claim. A phone showing "1.x M events/sec, locally,
no server" is more credible than any quoted benchmark — and sidesteps the "native
is not benchmarked, don't cite numbers" constraint entirely.

`bench()` requirements:
- warm-up iterations discarded;
- monotonic clock (`performance.now()`), optional per-event timing for p50/p99;
- a pre-built event array (don't measure event construction);
- runs in a worker so the page stays responsive.

---

## 8. Relationship to the playground "TeaVM project type"

The playground gains a project type alongside the existing compile/AOT/DSL ones:

```
DSL source ──► cloud generate (existing) ──► SEP.java
                                              │
                                              ▼  NEW server-side stage
                                          TeaVM compile  ──► classes.wasm
                                              │
                                              ▼  shipped to browser
                            fluxtion-wasm-runtime.createProcessor(wasm)
                                              │
                              run events · show audit · bench() on-device
```

- The TeaVM compile is a **new metered server stage** (heavier than generation —
  seconds of JVM CPU). Fits the cloud-compilation revenue model: a pricier tier,
  gated (not every keystroke).
- The browser side is **entirely** `fluxtion-wasm-runtime`. Building the runtime
  well means the playground integration is "load wasm, drive it" — no bespoke glue.

So: the runtime is the prerequisite. Build it for the Node example → reuse
verbatim for the browser showcase → reuse verbatim for the playground + phone
demo.

---

## 9. Open items to prototype (before committing the API)

1. **WASM-GC export surface in TeaVM 0.14.1** — confirm how to export more than
   `main()` (e.g. `@Export` / JSExport equivalents for WASM-GC), and the typed
   object-interop path for Tier 2. *The Node reference example is this prototype.*
2. **Boundary cost** — measure Tier 1 JSON bridge per-event overhead; decide the
   Tier 2 trigger point.
3. **String/array marshalling** — the cheapest reliable way to pass a string and
   a small record across WASM-GC both directions.
4. **Audit/log egress** — route TeaVM `System.out` + Fluxtion audit log to a JS
   callback without blocking dispatch.
5. **State on cold start** — replay-from-audit rehydration (see
   `deployment-targets.md`) — runtime helper or app concern?
6. **TS type generation** — emit `.d.ts` for a SEP's event/query surface (hand
   facade v1; generated v2).

---

## 10. Phasing

| Phase | Deliverable | Unblocks |
|---|---|---|
| 0 | Baseline proven (done) — 1.0.7 SEP → WASM → Node, JVM-identical | everything |
| 1 | Java bootstrap + Tier-1 value bridge; JS `createProcessor/onEvent/query` | **Node reference example** |
| 2 | `subscribe`, audit tap, env detection, browser load | **browser accept-reject showcase** |
| 3 | `bench()` harness + worker | **phone performance demo** |
| 4 | Tier-2 typed adapter (generated) | high-throughput / honest perf numbers |
| 5 | npm publish, TS types, playground "TeaVM project type" server stage | **productised playground** |

---

## 11b. Export surface — PROVEN (2026-06-15)

The WASM-GC export mechanism is no longer an open question. Validated against
the released 1.0.7 runtime (`spike/teavm-dsl`, `IntHost` + `ExportMain` +
`run-export-test.mjs`):

- `@org.teavm.jso.JSExportClasses({Host.class})` on the entry class +
  `@org.teavm.jso.JSExport` on the host **constructor and instance methods**
  surfaces them to JS.
- JS side: `new teavm.exports.IntHost()` constructs the host (which constructs +
  `init()`s the SEP); `host.onEvent(5)` feeds an `int` across the boundary (the
  SEP ran — console showed `out:10`); `host.getEventCount()` returned `3`.
- `int` crosses both directions; the SEP is driven entirely from JS, not `main()`.
- Quirk: `Object.keys(teavm.exports)` is `[]` — exports are reachable **by name**
  but not enumerable. The runtime loader must access exports by name, not iterate.
- Deps: `teavm-jso` (provided) alongside `teavm-classlib`.

This pins Tier-1's mechanism. Strings (the value bridge) are the next thing to
confirm cross both ways; object interop (Tier-2) remains to prototype.

---

## 12. Conformance harness — dual Java / Node SEP runners (JUnit)

The end state (user direction, 2026-06-15): a Maven project that runs the **whole
loop** — author the SEP in Fluxtion → JVM JUnit test → compile to WASM → host it →
run → assert — and crucially runs the **same test spec against both runtimes** so
JVM and WASM are proven equivalent automatically.

### Shape

```
   SepSpec  (events in  →  expected outputs)        ← one source of truth
      │
      ├──────────────►  JavaSepRunner   (drives the SEP on the JVM, in-process)
      │                                              ↘
      └──────────────►  NodeSepRunner   (drives the WASM SEP via Node subprocess) ──► assertEquals
```

- **`SepRunner`** — a common interface: `init()`, `onEvent(e)`, `drain()/getResult()`.
  Two implementations:
  - `JavaSepRunner` — constructs the generated SEP class, calls `onEvent`, reads
    the sink — pure JVM.
  - `NodeSepRunner` — spawns Node, loads the `.wasm` via `fluxtion-wasm-runtime`,
    feeds the same events, captures outputs back over stdout/IPC.
- **`SepSpec`** — events + expected outputs, runtime-agnostic. The same spec object
  feeds both runners.
- **JUnit conformance test** — parameterised over `{JVM, WASM}` (mirrors the
  existing `MultipleSepTargetInProcessTest` axis pattern in fluxtion-integration-tests);
  asserts each runner reproduces the spec, and that WASM == JVM byte-for-byte.

### What it needs

1. **TeaVM compile invoked from the build/test.** Either the `teavm-maven-plugin`
   bound to a pre-integration-test phase, or the TeaVM tooling API called directly
   from a test fixture. (~seconds/SEP — an integration-test concern, not a unit test.)
2. **A WASM execution channel from the JVM.** Pragmatic v1: **Node subprocess**
   (Node has solid WASM-GC; the JVM test shells out and compares stdout). v2 option:
   an **embedded JVM WASM runtime** (Chicory / GraalWasm) to avoid the Node
   dependency — *blocked on their WASM-GC support, which is currently partial;
   keep Node as the reference channel.*
3. **The bootstrap host's API must match the JVM driver's** so one `SepSpec`
   drives both — i.e. `onEvent`/`getResult` mean the same thing on both sides.
   (The proven `IntHost` is the WASM half; `JavaSepRunner` is the JVM half.)

### Why it matters

This is the automation of "confirm each operator runs in WASM" (currently a manual
task). Every operator + DSL shape gets a JVM-vs-WASM equivalence test — the
regression guard that lets WASM be trusted as a target, not just demoed.

---

## 14. Status — v0.1.0 productised (2026-06-16)

The package now exists at top-level `fluxtion-wasm-runtime/` and is published-ready
(`@telamin/fluxtion-wasm-runtime`, Apache-2.0, zero deps, single ESM file):

**Built (the proven host surface):**
- `createProcessor(wasm, opts?)` — feature-gates WASM-GC, auto-loads the
  `*.wasm-runtime.js` glue (Node: dynamic import of a file URL; browser: reuses the
  preloaded `globalThis.TeaVM`, else imports the glue), `TeaVM.wasmGC.load`s the
  module, returns a `FluxtionProcessor`.
- `FluxtionProcessor.newHost(className, ...args)` — constructs an `@JSExportClasses`
  host by name (exports are name-reachable, not enumerable), with a clear error.
- `detectEnv()` / `isWasmGcSupported()` — environment + capability gates.
- `bench(step, opts?)` — measured, never-asserted throughput (`eventsPerSecond`,
  optional `p50/p99`, `device`); produces the honest on-device phone number.
- TS `types/index.d.ts`, README, and a `node --test` smoke suite that loads the
  capabilities demo's real SEP wasm and drives it (5/5 green).

**Consumed (productised = actually used, not just published):**
- capabilities Node probe runner → `createProcessor` + `newHost` (all 15 probes ✅).
- browser demos (capabilities + showcase) vendor `src/index.mjs` verbatim as
  `web/fluxtion-wasm-runtime.js` via `npm run sync`; showcase `app.js` migrated to
  the `createProcessor`/`newHost` facade.

**Still spec (next phases):** the generic Tier-1 JSON bridge (`onEvent`/`query`
against a generic Java bootstrap host — today each SEP exports a bespoke host),
the Tier-2 typed fast-path adapter (generator-emitted), `subscribe`/`onAudit`
taps as first-class runtime verbs, a worker wrapper, and the actual `npm publish`.

---

## 13. Naming / packaging

- npm: `@telamin/fluxtion-wasm-runtime` (JS/TS host bindings).
- Java: `fluxtion-wasm-bootstrap` (Maven; compiled into each SEP wasm). Could
  live in the same repo as a sibling module, or fold into the generator catalog.
- Repo: its own top-level project (`fluxtion-wasm-runtime`) as the user intends;
  the `fluxtion-wasm-testharness` spikes become its conformance/bench tests.
