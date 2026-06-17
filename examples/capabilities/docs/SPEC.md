# SPEC — fluxtion-wasm-capabilities

## Goal

A browser-hosted, self-proving reference of **which Fluxtion capabilities survive
compilation to WebAssembly**, for newcomers evaluating Fluxtion-on-WASM. Output:
a single static page with a **capability grid** and **live demos**, where each
row's support status is produced by the demo running in WASM in the visitor's
browser — not asserted by us.

## Why it matters

`onEvent` + DSL map/filter are proven, but `DataFlow` has a whole control plane
beyond events (sinks, streamed-state reads, signals, manual triggers, services,
audit + log-level control). Some of it is reflective and will NOT survive WASM.
A newcomer needs to know — honestly, with the real error — what works before they
design for it.

## Capability surface

Grouped by predicted risk (the probe pass replaces predictions with facts in
`PHASING.md`):

| Group | Capabilities | Prediction |
|---|---|---|
| **Proven** | `onEvent`, DSL `map/filter/peek/push/binaryMap/groupBy`, `addSink`/`removeSink` | ✅ (conformance + showcase) |
| **Low-risk (event-based)** | `publishSignal`, `triggerCalculation`, change log level (`EventLogControlEvent`/`LogLevel`) | ✅ predicted |
| **Must test** | audit logging (`LogRecordListener`), `getStreamed`/`getNodeById` (reflective fallback), exported services (`@ExportService`), buffer/window | ⚠️ unknown |
| **Likely unsupported** | injected services (`@ServiceRegistered`), instance callbacks, `@Inject` (all reflective) | ❌ predicted |

## Design

### Single Maven module

The generated SEP is **committed**, so there is no generator↔SEP build cycle and
everything fits one module (nodes + SEP + host + `@JSExport` + TeaVM + JVM tests).
`fluxtion-builder` is present only for the manual generator; TeaVM compiles from
`ExportMain` and never reaches it. See `CLAUDE.md` for the proven-facts list.

### One SEP, many probes

A single SEP exercises every capability (DSL flows + imperative nodes + audit
enabled). `CapabilitiesHost` (plain Java) exposes **one probe method per
capability** that performs the capability and returns a result string (or throws).
`CapHost` is the thin `@JSExport` wrapper. The same host runs on the JVM (the JUnit
test is the JVM reference) and in WASM (the browser).

### Self-reporting grid

`web/app.js` calls each probe inside `try/catch`:
- success → ✅ + the live result rendered in the demo panel;
- throw → ❌ + the real exception message (TeaVM surfaces unsupported ops as
  runtime errors).

So the grid is always truthful and doubly useful: a newcomer sees both *that* a
capability is unsupported and *why*.

### Extractable JS runtime

`web/fluxtion-wasm-runtime.js` is written as a standalone library — `createProcessor`,
lifecycle, and the boundary helpers — with no app-specific code, so it can be lifted
verbatim into the future `@telamin/fluxtion-wasm-runtime` npm package (see
`../docs/fluxtion-wasm-runtime-spec.md`). `app.js` imports from it; it never calls
`globalThis.TeaVM` directly. This is the productized API being born in place.

## Web UI

```
┌─ Fluxtion in WebAssembly — capability support ───────────────┐
│  capability            supported   demo                       │
│  DSL map/filter           ✅       [Run ▾]  (live pipeline)     │
│  Sinks (egress)           ✅       [Run ▾]                      │
│  getStreamed              ?        [Run ▾]                      │
│  Signals                  ?        [Run ▾]                      │
│  Audit logging            ?        [Run ▾]  (live log output)   │
│  Change log level         ?        [Run ▾]                      │
│  Exported services        ?        [Run ▾]                      │
│  Injected services        ❌       [Run ▾]  (shows the error)   │
│  …                                                            │
└──────────────────────────────────────────────────────────────┘
```

Each `[Run ▾]` expands a small panel demoing that one capability against the live
WASM. Audit logging gets a richer panel (feed an event, see the captured log lines).

## Out of scope

Host-integration: data connectors, event feeds, agents, threads, file IO — these
are the host's responsibility, not the SEP's, and are intentionally absent from WASM.

## Success criteria

1. `mvn clean install` (Java 21) green: JVM tests + wasm built + copied to `web/`.
2. Every grid row's status produced by a real WASM run (Node probe + in-browser).
3. The page is genuinely informative to a newcomer with zero Fluxtion context.
4. `web/fluxtion-wasm-runtime.js` is clean enough to extract to npm unchanged.
