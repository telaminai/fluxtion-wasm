# fluxtion-wasm

Run a **Fluxtion event processor in WebAssembly** — in the browser, Node, a web
worker, or at the edge. Deterministic, single-threaded, no server and no AI on the
path; the same logic as the JVM, byte-for-byte.

> **Status: in development.** The pipeline is proven (a generated SEP → TeaVM → WASM-GC
> runs JVM-identically; the JSON bridge drives any SEP). No performance numbers are
> quoted — the runtime ships a `bench()` so you measure honest, on-device throughput.

## Layout

| Path | Artifact | What it is |
|---|---|---|
| [`bootstrap/`](bootstrap) | Maven `com.telamin.fluxtion:fluxtion-wasm-bootstrap` | the **Java host layer** compiled into your SEP wasm — the generic JSON bridge (`JsonBridgeHost`, `StringEvent`, `MiniJson`, `SinkListener`) |
| [`runtime/`](runtime) | npm `@telamin/fluxtion-wasm-runtime` | the **JS host lib** — loads the wasm, gives you `onEvent` / `query` / `subscribe` / `bench` |
| [`examples/capabilities/`](examples/capabilities) | — | the **reference app**: a self-proving capability grid + a Live Order Desk, all driven through the bridge |
| [`docs/`](docs) | — | [getting-started](docs/getting-started.md), the design specs |

The two libs are the product; the example is how you use them. The
[`fluxtion-wasm-testharness`](https://github.com/telaminai/fluxtion-wasm-testharness)
repo is the proving ground (conformance harness proving JVM == WASM, benchmarks,
spikes) and consumes the published libs.

## Quick start

```bash
# Java side — build the bridge lib + the example (TeaVM compiles to WASM-GC; needs Java 21)
mvn install                       # builds bootstrap, then examples/capabilities

# serve the example (http, not file://)
cd examples/capabilities/web && python3 -m http.server 8000   # → http://localhost:8000
```

```js
// JS side — drive any SEP with plain objects
import { createProcessor } from '@telamin/fluxtion-wasm-runtime';

const sep = (await createProcessor('./classes.wasm')).jsonBridge('JsonHost');
sep.track('trades');
sep.onEvent({ type: 'trade', symbol: 'EURUSD', qty: 100 });
sep.query('trades');   // → { symbol: 'EURUSD', qty: 100 }
```

Full walkthrough — prerequisites, the pipeline, a hello-SEP, the bridge API, build
config, deploy targets, honest limits — in **[docs/getting-started.md](docs/getting-started.md)**.

## Requirements

- A **WASM-GC** host: recent Chrome/Firefox/Safari, or Node 20+.
- **Java 21** + a Fluxtion **generator** (cloud key in `~/.fluxtion`, or local
  `fluxtion-generator-core`) to produce a SEP. Generation always needs a generator.

## License

Apache-2.0.
