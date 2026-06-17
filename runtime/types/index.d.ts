// Type definitions for @telamin/fluxtion-wasm-runtime

export type HostEnv = 'node' | 'browser' | 'worker';

export interface ProcessorOptions {
  /** Host environment. Default: auto-detect. */
  env?: 'auto' | HostEnv;
  /** Override for the TeaVM glue URL. Default: the `*.wasm-runtime.js` sibling of the wasm. */
  runtimeUrl?: string | URL;
}

/**
 * Thin wrapper over a loaded TeaVM module. The SEP's exported host classes are the
 * real API; this only constructs them by name.
 */
export class FluxtionProcessor {
  /** Raw exported classes — reachable by name, not enumerable. */
  readonly exports: Record<string, unknown>;
  /** Resolved host environment. */
  readonly env: HostEnv | 'unknown';
  /**
   * Construct an exported host class by name (an `@JSExportClasses` entry).
   * @param className e.g. "CapHost"
   * @param args constructor arguments
   */
  newHost<T = any>(className: string, ...args: unknown[]): T;

  /**
   * Wrap a generic JSON-bridge host so a JS caller drives the SEP with plain
   * objects — no Java, no wire strings. The host must expose the fixed bridge
   * surface (onEventJson/onEvent/addSink/lastSink/subscribe/signal/drainAudit/
   * setLogLevel). Default class name: "JsonHost".
   */
  jsonBridge(className?: string, ...args: unknown[]): JsonBridge;
}

/**
 * Plain-JS facade over a generic JSON-bridge host: send events as objects, read or
 * subscribe to named sinks, drive signals and audit — one runtime, any SEP.
 */
export class JsonBridge {
  constructor(host: any);
  /** The underlying exported host, for capabilities beyond this facade. */
  readonly host: any;
  /** Dispatch an event object (`{type, ...}`) or JSON string; `type` routes it. */
  onEvent(event: object | string): void;
  /** Dispatch an explicit (type, payload) event without JSON parsing. */
  send(type: string, payload: string): this;
  /** Pre-register a sink so a later `query` sees its values (pull-style). */
  track(sinkId: string): this;
  /** Latest value at a named sink (JSON-parsed when it is JSON), or null. */
  query<T = unknown>(sinkId: string): T | null;
  /** Current value of a flow node by its `.id(...)` tag (JSON-parsed when JSON). */
  get<T = unknown>(flowId: string): T | null;
  /** Push every value reaching a named sink to `cb` (drives the UI). */
  subscribe(sinkId: string, cb: (value: unknown) => void): this;
  /** Wire a JS-backed service: `register` receives the raw host to install it. */
  registerService(register: (host: any) => void): this;
  /** Publish a named signal. */
  signal(name: string, value: unknown): this;
  /** Drain audit records captured since the last call. */
  audit(): string;
  /** Retune the audit log level at runtime: TRACE/DEBUG/INFO/WARN/ERROR. */
  setLogLevel(level: string): this;
}

export interface BenchOptions {
  /** Iterations to measure (default 100000). */
  events?: number;
  /** Discarded warm-up iterations (default min(events, 10000)). */
  warmup?: number;
  /** Record per-event timing for p50/p99 (adds overhead). */
  percentiles?: boolean;
  /** Monotonic clock returning milliseconds (default performance.now). */
  now?: () => number;
}

export interface BenchResult {
  events: number;
  millis: number;
  eventsPerSecond: number;
  /** Measured user-agent / Node info — reported, not claimed. */
  device: string;
  p50Nanos?: number;
  p99Nanos?: number;
}

/** Best-effort detection of the JS host environment. */
export function detectEnv(): HostEnv;

/** Coarse capability gate: whether WebAssembly is available to instantiate a WASM-GC module. */
export function isWasmGcSupported(): boolean;

/** Load a TeaVM-compiled Fluxtion SEP and return a processor. */
export function createProcessor(
  wasm: string | URL,
  opts?: ProcessorOptions,
): Promise<FluxtionProcessor>;

/** Run `step(i)` in a tight loop and report measured throughput for this device. */
export function bench(step: (i: number) => void, opts?: BenchOptions): BenchResult;
