// VENDORED COPY — do not edit here.
// Source of truth: fluxtion-wasm-runtime/src/index.mjs (@telamin/fluxtion-wasm-runtime).
// Re-sync with: (cd fluxtion-wasm-runtime && npm run sync)

// @telamin/fluxtion-wasm-runtime — host bindings for a TeaVM-compiled Fluxtion SEP.
//
// A Fluxtion event processor (SEP) is generated as plain Java, compiled by TeaVM
// to WASM-GC (`classes.wasm` + a `classes.wasm-runtime.js` loader glue), and then
// driven from JavaScript. This package is the thin, app-agnostic layer between
// "a .wasm file" and "a usable processor" — it knows nothing graph-specific
// (operators, event types and wiring are baked into the SEP wasm itself); it only
// knows the four things a host needs: detect the environment, load the module,
// hand back the SEP's exported host classes, and (optionally) benchmark a loop.
//
// One file, zero dependencies, browser- and Node-safe (Node built-ins are loaded
// lazily and only on the Node path), so the same source ships to npm and is
// vendored verbatim into each browser demo's web/ directory.
//
// Usage (Node):
//   import { createProcessor } from '@telamin/fluxtion-wasm-runtime';
//   const proc = await createProcessor('./target/wasm-gc/classes.wasm');
//   const host = proc.newHost('CapHost');     // an @JSExportClasses entry
//   host.dsl(5);                               // call its @JSExport methods
//
// Usage (browser): preload the TeaVM glue once, then load by URL —
//   <script src="./classes.wasm-runtime.js"></script>   <!-- sets globalThis.TeaVM -->
//   <script type="module">
//     import { createProcessor } from './fluxtion-wasm-runtime.js';
//     const proc = await createProcessor('./classes.wasm');
//   </script>

/**
 * Best-effort detection of the JS host environment.
 * @returns {'node'|'worker'|'browser'}
 */
export function detectEnv() {
  if (typeof process !== 'undefined' && process.versions && process.versions.node
      && typeof window === 'undefined') {
    return 'node';
  }
  if (typeof importScripts === 'function'
      || (typeof self !== 'undefined' && typeof window === 'undefined')) {
    return 'worker';
  }
  return 'browser';
}

/**
 * Whether this environment can plausibly instantiate a WASM-GC module. This is a
 * coarse capability gate (WebAssembly present); a genuinely missing GC backend on
 * an old browser surfaces as a clear error at {@link createProcessor} load time
 * rather than here.
 * @returns {boolean}
 */
export function isWasmGcSupported() {
  return typeof WebAssembly !== 'undefined' && typeof WebAssembly.validate === 'function';
}

/** Derive the `*.wasm-runtime.js` glue URL that sits beside a `*.wasm`. */
function deriveRuntimeUrl(wasm) {
  return String(wasm).replace(/\.wasm(\?.*)?$/, '.wasm-runtime.js$1');
}

/**
 * Make sure the TeaVM WASM-GC loader glue is present (sets `globalThis.TeaVM`).
 * If the page already preloaded it via a <script> tag (the browser demos do this),
 * reuse it; otherwise dynamically import the glue module.
 */
async function ensureTeaVM(runtimeUrl, env) {
  const existing = globalThis.TeaVM;
  if (existing && existing.wasmGC) return existing;

  let url = String(runtimeUrl);
  if (env === 'node' && !/^[a-z][a-z0-9+.-]*:/i.test(url)) {
    const { pathToFileURL } = await import('node:url');
    url = pathToFileURL(url).href;
  }
  await import(/* @vite-ignore */ url);

  const tv = globalThis.TeaVM;
  if (!tv || !tv.wasmGC) {
    throw new Error(
      `loaded '${url}' but globalThis.TeaVM.wasmGC is not set — that file does not ` +
      `look like a TeaVM WASM-GC runtime glue (classes.wasm-runtime.js).`
    );
  }
  return tv;
}

/**
 * @typedef {Object} ProcessorOptions
 * @property {'auto'|'node'|'browser'|'worker'} [env]  host environment (default: auto-detect)
 * @property {string|URL} [runtimeUrl]  override for the TeaVM glue URL (default: sibling of the wasm)
 */

/**
 * Load a TeaVM-compiled Fluxtion SEP and return a {@link FluxtionProcessor}.
 * The only asynchronous step in the SEP lifecycle — dispatch itself is synchronous.
 *
 * @param {string|URL} wasm  URL/path of the compiled `classes.wasm`
 * @param {ProcessorOptions} [opts]
 * @returns {Promise<FluxtionProcessor>}
 */
export async function createProcessor(wasm, opts = {}) {
  if (!isWasmGcSupported()) {
    throw new Error(
      'WebAssembly is unavailable in this environment — a WASM-GC capable runtime ' +
      '(recent Chrome/Firefox/Safari, or Node 20+) is required to run a Fluxtion SEP.'
    );
  }
  const env = opts.env && opts.env !== 'auto' ? opts.env : detectEnv();
  const runtimeUrl = opts.runtimeUrl != null ? opts.runtimeUrl : deriveRuntimeUrl(wasm);
  const tv = await ensureTeaVM(runtimeUrl, env);
  const teavm = await tv.wasmGC.load(String(wasm));
  return new FluxtionProcessor(teavm, { env });
}

/**
 * Thin wrapper over a loaded TeaVM module. The SEP's host classes (the
 * `@JSExportClasses` entries, each with `@JSExport` constructor + methods) are the
 * real API; this class only hands them back. Graph-specific verbs live on the host.
 */
export class FluxtionProcessor {
  /**
   * @param {*} teavm  the loaded TeaVM module
   * @param {{env?: string}} [meta]
   */
  constructor(teavm, meta = {}) {
    this._teavm = teavm;
    /** the raw exported classes — reachable by name, NOT enumerable (Object.keys is []) */
    this.exports = teavm.exports;
    /** the resolved host environment */
    this.env = meta.env || 'unknown';
  }

  /**
   * Construct an exported host class by name (the `@JSExportClasses` entry). The
   * host's constructor typically builds the SEP and calls `init()`.
   * @param {string} className  e.g. "CapHost"
   * @param {...any} args  constructor arguments
   * @returns {*} the host instance, exposing its `@JSExport` methods
   */
  newHost(className, ...args) {
    const Ctor = this.exports[className];
    if (typeof Ctor !== 'function') {
      throw new Error(
        `exported class not found: '${className}'. Is it listed in @JSExportClasses, ` +
        `with @JSExport on its constructor? (TeaVM exports are reachable by name only.)`
      );
    }
    return new Ctor(...args);
  }

  /**
   * Wrap a generic JSON-bridge host (the SEP-agnostic `JsonHost` / `JsonBridgeHost`
   * shell) so a JS caller drives the SEP with plain objects and never touches Java
   * or wire strings. The host must expose onEventJson/onEvent/addSink/lastSink/
   * subscribe/signal/drainAudit/setLogLevel (the fixed bridge surface).
   * @param {string} [className]  the exported bridge host (default "JsonHost")
   * @param {...any} args
   * @returns {JsonBridge}
   */
  jsonBridge(className = 'JsonHost', ...args) {
    return new JsonBridge(this.newHost(className, ...args));
  }
}

/** Parse a sink value as JSON when it looks like JSON, else return the raw string. */
function maybeJson(s) {
  if (s == null) return null;
  const t = s.trim();
  if (t.length && (t[0] === '{' || t[0] === '[')) {
    try { return JSON.parse(t); } catch { /* fall through */ }
  }
  return s;
}

/**
 * Plain-JS facade over a generic JSON-bridge host. Send events as objects, read or
 * subscribe to named sinks, drive signals and audit — no Java, no wire format. This
 * is the "a JS dev installs it and ships" surface: one runtime, any SEP, JSON in/out.
 */
export class JsonBridge {
  /** @param {*} host  a JsonHost instance from {@link FluxtionProcessor.newHost} */
  constructor(host) {
    this._host = host;
  }

  /**
   * Dispatch an event. Pass an object (`{type:'json', symbol:'EURUSD', qty:100}`) —
   * its `type` field routes it to the graph's decoder for that type — or a JSON string.
   * @param {object|string} event
   */
  onEvent(event) {
    this._host.onEventJson(typeof event === 'string' ? event : JSON.stringify(event));
  }

  /** Dispatch an explicit (type, payload) event without JSON parsing. */
  send(type, payload) {
    this._host.onEvent(String(type), String(payload));
    return this;
  }

  /**
   * Start capturing a named sink so a later {@link query} sees its value. Call once
   * before sending events (pull-style reads only see values captured after this).
   * Not needed for {@link subscribe}, which registers as it wires the callback.
   */
  track(sinkId) {
    this._host.addSink(String(sinkId));
    return this;
  }

  /** Read the latest value at a named sink (JSON-parsed when it is JSON), or null. */
  query(sinkId) {
    return maybeJson(this._host.lastSink(sinkId));
  }

  /** Read the current value of a flow node by its `.id(...)` tag (JSON-parsed when JSON). */
  get(flowId) {
    return maybeJson(this._host.getStreamed(flowId));
  }

  /**
   * Push every value reaching a named sink to `cb` (JSON-parsed when it is JSON).
   * Use it to drive the UI: a sink value becomes a DOM update.
   * @param {string} sinkId
   * @param {(value: unknown) => void} cb
   */
  subscribe(sinkId, cb) {
    this._host.subscribe(sinkId, (json) => cb(maybeJson(json)));
    return this;
  }

  /** Register a JS-backed service implementation by handing the host the impl. */
  registerService(register) {
    // service wiring is host-specific; expose the raw host for advanced cases
    register(this._host);
    return this;
  }

  /** Publish a named signal. */
  signal(name, value) {
    this._host.signal(String(name), String(value));
    return this;
  }

  /** Drain the SEP's audit records captured since the last call. */
  audit() {
    return this._host.drainAudit();
  }

  /** Retune the audit log level at runtime: TRACE/DEBUG/INFO/WARN/ERROR. */
  setLogLevel(level) {
    this._host.setLogLevel(String(level));
    return this;
  }

  /** The underlying exported host, for capabilities beyond the bridge facade. */
  get host() {
    return this._host;
  }
}

// ── benchmark harness ───────────────────────────────────────────────────────
// Measured, never asserted. bench() reports what THIS device did with THIS SEP —
// no performance number is baked into the package. Use it to produce honest,
// on-device throughput (e.g. a phone running the SEP locally), not to claim figures.

function defaultNow() {
  if (typeof globalThis.performance !== 'undefined' && globalThis.performance.now) {
    return () => globalThis.performance.now();
  }
  // Node fallback (older runtimes without a global performance)
  return () => Number(process.hrtime.bigint()) / 1e6;
}

function deviceInfo() {
  if (typeof navigator !== 'undefined' && navigator.userAgent) return navigator.userAgent;
  if (typeof process !== 'undefined' && process.versions) {
    return `node ${process.versions.node} ${process.platform}/${process.arch}`;
  }
  return 'unknown';
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return sorted[idx];
}

/**
 * @typedef {Object} BenchOptions
 * @property {number} [events]       iterations to measure (default 100000)
 * @property {number} [warmup]       discarded warm-up iterations (default min(events,10000))
 * @property {boolean} [percentiles] record per-event timing for p50/p99 (adds overhead)
 * @property {() => number} [now]    monotonic clock returning millis (default performance.now)
 */

/**
 * @typedef {Object} BenchResult
 * @property {number} events
 * @property {number} millis
 * @property {number} eventsPerSecond
 * @property {string} device          measured user-agent / node info — not claimed
 * @property {number} [p50Nanos]
 * @property {number} [p99Nanos]
 */

/**
 * Run `step(i)` in a tight loop and report measured throughput for this device.
 * Warm-up iterations are discarded. Build the event payloads BEFORE calling so
 * the loop measures dispatch, not event construction.
 *
 * @param {(i: number) => void} step  one unit of work (e.g. host.onEvent(events[i]))
 * @param {BenchOptions} [opts]
 * @returns {BenchResult}
 */
export function bench(step, opts = {}) {
  const events = opts.events ?? 100_000;
  const warmup = opts.warmup ?? Math.min(events, 10_000);
  const now = opts.now ?? defaultNow();

  for (let i = 0; i < warmup; i++) step(i);

  const timings = opts.percentiles ? new Float64Array(events) : null;
  const t0 = now();
  if (timings) {
    for (let i = 0; i < events; i++) {
      const a = now();
      step(i);
      timings[i] = now() - a;
    }
  } else {
    for (let i = 0; i < events; i++) step(i);
  }
  const millis = now() - t0;

  const result = {
    events,
    millis,
    eventsPerSecond: millis > 0 ? events / (millis / 1000) : Infinity,
    device: deviceInfo(),
  };
  if (timings) {
    timings.sort();
    result.p50Nanos = percentile(timings, 50) * 1e6;
    result.p99Nanos = percentile(timings, 99) * 1e6;
  }
  return result;
}
