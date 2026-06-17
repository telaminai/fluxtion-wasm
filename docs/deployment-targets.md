# Deployment targets — where the WASM artefact runs

Same TeaVM-compiled `.wasm` module, six different runtimes. Honest
characterisation of each — cold-start cost, integration pattern, and the
state-on-cold-start strategy that matters for stateful Fluxtion SEPs.

## Cold-start comparison (honest numbers)

| Target | Cold start | Per-event latency (WASM) | Notes |
|---|---|---|---|
| Native JVM Fluxtion (baseline) | seconds | sub-µs | Reference; not what we ship here |
| Java Lambda (vanilla JVM) | 1–3 s | sub-µs | What we're displacing |
| Java Lambda + SnapStart | 200–500 ms | sub-µs | Better, still Java-only, still has constraints |
| **Node Lambda + your WASM** | **~200–400 ms** | **~µs-tier** | Node init dominates; WASM instantiate is small |
| Native binary via Lambda custom runtime + wasmtime | ~50–150 ms | ~µs-tier | Skip Node entirely |
| **Cloudflare Workers** | **5–50 ms** | **~µs-tier** | V8 isolate; this is the killer target |
| Fastly Compute@Edge | ~5–50 ms | ~µs-tier | WASM-native; comparable to Workers |
| WasmEdge / wasmtime self-hosted | ~50–100 ms | ~µs-tier | For on-prem / sovereign deployments |

"Almost zero" is the right framing for **Cloudflare Workers / Fastly**, not for
Lambda. Lambda is ~5–10× better than Java Lambda, which is a real win, but
Node init is a 150 ms floor we can't escape *inside* Lambda.

## Target 1 — Node.js (npm package)

The default. Consumers `npm install` the package; it loads the WASM via
`WebAssembly.instantiate()` and exposes a typed JS facade.

```js
import { createProcessor } from '@telamin/fx-pnl-processor';

const processor = await createProcessor();
processor.onEvent({ kind: 'trade', ... });
const position = processor.query('positionFor', 'EURUSD');
```

**Integration shape**:

- Single WASM artefact, sub-MB gzipped.
- TypeScript bindings emitted from the Java surface (TeaVM can generate; or
  hand-written facade against TeaVM's JS callable interface).
- Works in any Node 18+ (WASM support is universal).
- No native dependencies, no `node-gyp`, no per-platform builds.

**State persistence**: native to the Node process. Long-running Node services
keep state in WASM linear memory. On restart, replay from the audit log to
rehydrate — see *State on cold start* below.

## Target 2 — Browser

Same `.wasm` artefact, loaded via `WebAssembly.instantiate()`. Two use cases:

- **Embedded analytics widget** — a SaaS UI that runs streaming aggregations
  client-side, no server round-trip.
- **Local-first apps** — process user events locally; sync to server in
  background. The replay capability handles offline / re-sync.

Bundle is the same sub-MB module. No additional engineering work beyond what
the Node target needs.

## Target 3 — AWS Lambda (Node runtime)

Wire the WASM into a Lambda function via the standard Node runtime. The
critical point: **the Kinesis / SQS / DynamoDB Streams reader is a node in
the Fluxtion graph, compiled into the WASM** — not a host-side JS adapter.
The DSL declares `feed: kinesis`, `fluxtion-gen` selects the Kinesis-feed
implementation from its catalog, binds it as a graph node, and the generated
SEP knows how to decode Kinesis records itself.

The Lambda handler that the orchestrator emits is therefore generic and
identical across all feed types — it just hands the raw event payload to
the WASM and the in-graph feed node does the rest:

```js
const { onEvent } = await createProcessor();  // top-level: warms on cold start

export async function handler(event) {
    // Generic forwarder. The graph's feed node knows it's Kinesis (or SQS,
    // or DynamoDB Streams) because that was decided at fluxtion-gen time.
    // No per-source decoder lives here.
    onEvent(event);
    return { statusCode: 200 };
}
```

The handler is generated automatically by the `git push → deploy` orchestrator;
the developer never writes it. Per-target work is IAM (the function needs
permission to read Kinesis / write SQS / etc.) and deploy mechanics — not
per-target adapter code.

**What you gain over Java Lambda**:

- No JVM, no `-Xms` / `-Xmx`, no GC tuning, no SnapStart configuration, no
  Lambda Layer for the JRE, no JIT warm-up window.
- Cold start: ~200–400 ms (Node init + WASM instantiate). 5–10× better than
  vanilla Java Lambda.
- Smaller deployment package. Lambda's 50 MB zip / 250 MB unzipped limit is
  not even close to binding.

**What you don't gain**: native edge-tier cold start. Lambda's Node runtime
is the floor. If you need sub-50 ms cold start, see *Target 4* below.

**State on cold start**: warm-instance reuse + audit-log replay. See dedicated
section below.

## Target 4 — Cloudflare Workers / Fastly Compute@Edge

The unlock target. WASM-native runtimes, sub-50 ms cold start, isolate-per-request.

**Cloudflare Workers specifics**:

- WASM size limit: 1 MB compressed (free tier), 10 MB compressed (paid tier).
  A tightly-compiled Fluxtion SEP fits comfortably in the paid tier; should
  also fit free tier for typical processors. Subject to harness item 2.
- Durable Objects: state-with-an-id, sharded by key. Pairs naturally with the
  WASM SEP — the Durable Object holds the WASM state, the WASM does the
  computation.
- KV / R2 for audit-log storage and replay.
- Per-request CPU time limits (10 ms free, 50 ms paid, configurable up to
  30 s on Unbound). Fits Fluxtion's typical processing well.

**Why this is the differentiated target**:

- **Nobody else can deploy stateful, replay-capable event processors to
  Workers.** Cloudflare's own Durable Objects is the only competitor and it's
  a different model (key-sharded actors vs. flow-graph SEPs).
- The cold-start profile (5–50 ms) makes Fluxtion usable for genuinely
  per-request workloads, not just always-on streams.
- Workers ships globally by default. A single deploy of your WASM is in 300+
  cities the next minute.

**State on cold start**: Durable Objects hold WASM linear memory between
requests within an object lifetime. Across cold starts, replay from the
audit log stored in KV/R2.

## Target 5 — Edge / WasmEdge / wasmtime / self-hosted

For sovereign / on-prem / regulated deployments where Cloudflare and Lambda
aren't options. The WASM artefact is identical; the host runtime is your
choice of WASM engine (wasmtime, WasmEdge, Wasmer, etc.).

Typical packaging: a tiny Go or Rust binary that loads the `.wasm`, exposes
gRPC / HTTP / Kafka consumer entry points, and feeds events into
`onEvent`. Ops surface is one binary instead of a JVM + dependencies.

**State on cold start**: same audit-log replay model. The host binary owns
checkpointing; the WASM module owns deterministic event processing.

## Target 6 — IoT / embedded

WASM runtimes (WasmEdge, WAMR) ship for ARM and constrained environments. A
sub-MB Fluxtion SEP fits a $5 IoT gateway and processes events locally —
edge analytics without a cloud round-trip.

This is the most speculative target. Real but slow-moving (driver / toolchain
/ certification overhead). 5-year story not 1-year. Worth keeping the WASM
artefact small enough to leave this option open.

## AWS event-stream integration patterns

The Fluxtion graph chooses its feed at DSL/config time, not at deploy time.
A processor compiled with `feed: kinesis` knows how to decode Kinesis records;
one compiled with `feed: sqs` doesn't (and doesn't need to). The Lambda
handler is the same generic forwarder either way.

Per-source notes — these describe what happens *inside* the compiled SEP's
feed node, not what the JS handler does:

| Source | Lambda integration | What the in-graph feed node does |
|---|---|---|
| Kinesis Data Streams | Event source mapping, polled batches | Decodes Kinesis records (base64 + your payload format); orders by shard; fans into the SEP |
| SQS | Event source mapping, polled batches | Decodes SQS message body; FIFO ordering preserved when source is FIFO |
| DynamoDB Streams | Event source mapping | Decodes the DynamoDB record (Keys / NewImage / OldImage / Eventname); CDC-shaped events |
| MSK (Kafka on AWS) | Event source mapping | Decodes Kafka records via the bundled deserializer; same ordering semantics as Kinesis |
| EventBridge | Async invocation | Routes by source / detail-type rules; payload pre-decoded by EventBridge |
| Kinesis Firehose | Not a direct source (transforms only) | Use Firehose for buffered delivery to S3 + S3 trigger to replay |

The JS handler stays mechanical and identical across sources — it forwards
the raw event to the WASM, the WASM's feed node decodes it. No
Fluxtion-specific complexity at the AWS layer, no per-source JS code.

## State on cold start — chosen per cache, not architecturally

Fluxtion processors are stateful. The compute targets above are all
"stateless between cold starts" (Lambda, Workers, anything serverless). The
cold-start strategy is **per-cache**, declared at DSL/config time, bound by
`fluxtion-gen` into the graph as a cache node. The catalog ships standard
implementations; users pick the one that matches the data shape.

| Cache strategy | Boundary crossings | Best for |
|---|---|---|
| **Eager** | Once at startup — load via host imports, then pure in-WASM | Small reference data (countries, currencies, FX rates, product catalogue) |
| **Replay-all** | Once at startup — replay from event source until caught up, then pure in-WASM | ktable-style state rebuilt from an event log (positions, balances) |
| **Latest-only** | Background subscribe — host pushes updates into WASM as they arrive; reads are pure WASM | Live reference data that changes (prices, rates, config) |
| **On-demand** | Per-access — synchronous WASM-import call into JS, JS calls DynamoDB/Redis/etc., returns to WASM | Sparse high-cardinality lookups (customer-by-ID where there are millions, few touched per second) |

The performance + replay-determinism story follows from the choice:

- **Eager / Replay-all / Latest-only** — no per-event JS boundary crossing
  after warm-up. Hot path is pure WASM. Deterministic replay works because
  state is rebuildable from the same inputs.
- **On-demand** — JS boundary per access; non-deterministic replay unless
  the lookup responses are themselves logged. Slower per-event because each
  miss is gated on external network latency.

A processor can mix strategies — a small countries cache (eager) + a
positions cache (replay-all) + a customer cache (on-demand). The graph
compiler emits whatever bridge code each strategy needs; the developer
declares intent.

### Fluxtion's deployment moat

**The deterministic replay capability is what makes serverless stateful event
processing tractable.** Other stream processors need a snapshot store and the
machinery to load it on cold start; Fluxtion's replay-all and latest-only
strategies use the input log *as* the snapshot — the SEP is the recovery
procedure. Combined with the per-cache strategy choice, this is structurally
something competitors can't easily match:

- KStreams needs RocksDB state stores + Kafka changelog topics + recovery
  on rebalance. Heavy operational footprint.
- Flink needs checkpointing infrastructure + state backends + savepoint
  management. Doesn't run in WASM at all.
- Cloudflare Durable Objects has its own model — actors with persistent
  storage — but it's per-key sharding, not flow-graph dispatch.

Fluxtion's "log is the snapshot" model collapses all of that into "configure
your cache strategy". Same SEP runs on Lambda warm instance, Lambda cold
start with replay, Workers Durable Object, or in-process Node — the strategy
choice is per-cache and the graph compiler handles the rest.
