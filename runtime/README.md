# @telamin/fluxtion-wasm-runtime

Host bindings for a **TeaVM-compiled Fluxtion SEP**. A Fluxtion event processor is
generated as plain Java, compiled by TeaVM to WASM-GC (`classes.wasm` + a
`classes.wasm-runtime.js` glue), and driven from JavaScript. This package is the
thin, app-agnostic layer between *a `.wasm` file* and *a usable processor*.

It knows nothing graph-specific — operators, event types and wiring are baked into
the SEP wasm itself. It does the four things a host needs: detect the environment,
load the module, hand back the SEP's exported host classes, and (optionally)
benchmark a dispatch loop. One file, zero dependencies, Node + browser + worker.

```
   Fluxtion DSL ──gen──► YourProcessor.java ──TeaVM──► classes.wasm + classes.wasm-runtime.js
                                                                  │ loaded by
                                          @telamin/fluxtion-wasm-runtime
                                                                  │ used by
                            Node app · browser page · web worker · edge worker · phone
```

## Install

```bash
npm install @telamin/fluxtion-wasm-runtime
```

## Use — Node

```js
import { createProcessor } from '@telamin/fluxtion-wasm-runtime';

const proc = await createProcessor('./target/wasm-gc/classes.wasm');
const host = proc.newHost('CapHost');   // an @JSExportClasses entry in your SEP build

host.dsl(5);                            // call the host's @JSExport methods
host.jsonDecode('{"symbol":"EURUSD","qty":100}');
```

`createProcessor` auto-loads the `classes.wasm-runtime.js` glue sitting beside the
`.wasm` (override with `{ runtimeUrl }`).

## Use — browser

Preload the glue once (it sets `globalThis.TeaVM`), then load by URL:

```html
<script src="./classes.wasm-runtime.js"></script>
<script type="module">
  import { createProcessor } from './fluxtion-wasm-runtime.js';
  const proc = await createProcessor('./classes.wasm');
  const host = proc.newHost('CapHost');
  console.log(host.dsl(5));
</script>
```

Static demos that can't resolve the bare package specifier vendor a verbatim copy
of `src/index.mjs` as `web/fluxtion-wasm-runtime.js` (kept in sync by
`npm run sync` — see below).

## API

| Export | Purpose |
|---|---|
| `createProcessor(wasm, opts?)` | load a SEP wasm → `FluxtionProcessor` (the only async step) |
| `FluxtionProcessor.newHost(name, ...args)` | construct an exported host class by name |
| `FluxtionProcessor.exports` / `.env` | raw exported classes; resolved environment |
| `detectEnv()` | `'node' \| 'browser' \| 'worker'` |
| `isWasmGcSupported()` | coarse capability gate before loading |
| `bench(step, opts?)` | measured dispatch-loop throughput for **this** device |

`opts` for `createProcessor`: `{ env?: 'auto'|'node'|'browser'|'worker', runtimeUrl?: string|URL }`.

### Generic JSON bridge — drive any SEP with plain objects

The `jsonBridge` facade needs **no bespoke Java verb per app**: send events as
objects, read/subscribe to named sinks, and observe audit — one runtime, any SEP.
The SEP exports a single fixed `JsonHost` shell (later generator-emitted); typing
stays in the graph (the decoder-node pattern), so there is no reflection.

```js
const proc = await createProcessor('./classes.wasm');
const sep = proc.jsonBridge('JsonHost');

// ingress: a plain object — its `type` routes it to the graph's decoder
sep.onEvent({ type: 'json', symbol: 'EURUSD', qty: 100 });

// egress (pull): register a sink, read its latest value (parsed when JSON)
sep.track('tradeOut');
sep.onEvent({ type: 'json', symbol: 'GBPUSD', qty: 250 });
sep.query('tradeOut');                 // → { symbol: 'GBPUSD', qty: 250 }

// egress (push): a sink value becomes a UI update
sep.subscribe('tradeOut', (trade) => renderRow(trade));

sep.signal('greet', 'world');          // named signals
sep.setLogLevel('DEBUG');              // retune audit at runtime
sep.audit();                           // drain audit records
```

| `JsonBridge` method | Purpose |
|---|---|
| `onEvent(obj\|json)` | dispatch an event; `type` field routes it |
| `send(type, payload)` | explicit (type, payload) ingress |
| `track(id)` / `query(id)` | pull a named sink's latest value |
| `subscribe(id, cb)` | push each sink value to a callback (UI) |
| `signal(name, value)` | publish a named signal |
| `audit()` / `setLogLevel(level)` | drain / retune audit |

### The host is the real API

TeaVM exports are **reachable by name but not enumerable** (`Object.keys(exports)`
is `[]`), so you construct hosts by name with `newHost`. Graph-specific verbs
(`dsl`, `onEventString`, `eventToSink`, `jsonDecode`, …) live on the host class your
SEP build exports with `@JSExportClasses` + `@JSExport`, not on this package — it
deliberately has no operator knowledge.

### Benchmarking — measured, never claimed

`bench()` reports what *this device* did with *this SEP*. No performance number is
baked into the package or its docs. Build the events first, then measure dispatch:

```js
import { bench } from '@telamin/fluxtion-wasm-runtime';
const r = bench((i) => host.dsl(i + 1), { events: 100_000, percentiles: true });
// { events, millis, eventsPerSecond, p50Nanos, p99Nanos, device }
```

A phone reporting *"1.x M events/sec, locally, no server"* is a measurement on the
user's own device — more credible than any quoted figure, and the honest way to
produce throughput numbers.

## Requirements

- A **WASM-GC** capable host: recent Chrome/Firefox/Safari, or Node 20+.
- A SEP compiled with **TeaVM 0.14.1** (`targetType=WEBASSEMBLY_GC`, Java 21) and a
  host class exported via `@org.teavm.jso.JSExportClasses` + `@org.teavm.jso.JSExport`.

## Develop

```bash
npm test          # node --test (loads the capabilities demo wasm if built; skips otherwise)
npm run sync      # copy src/index.mjs into the browser demos' web/ as a vendored file
```

## Status

`v0.1.0` — the proven host surface: environment detection, wasm load, host
construction, and the bench harness. The generic Tier-1 JSON event bridge
(`onEvent`/`query` against a generic bootstrap host) and the Tier-2 typed
fast-path adapter are the next phases — see
[`docs/fluxtion-wasm-runtime-spec.md`](../docs/fluxtion-wasm-runtime-spec.md).

## License

Apache-2.0.
