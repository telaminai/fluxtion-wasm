// Smoke test — load a real TeaVM-compiled Fluxtion SEP and drive it through the
// package's public API. Uses the capabilities demo's built artifact if present;
// skips (does not fail) when the wasm has not been built, so `npm test` is green
// on a fresh checkout. Build it with:
//   (cd ../examples/capabilities && mvn -q clean install)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  createProcessor,
  detectEnv,
  isWasmGcSupported,
  bench,
} from '../src/index.mjs';

const WASM = fileURLToPath(
  new URL('../../examples/capabilities/target/wasm-gc/classes.wasm', import.meta.url),
);
const haveWasm = existsSync(WASM);

test('detectEnv reports node under node:test', () => {
  assert.equal(detectEnv(), 'node');
});

test('isWasmGcSupported is true on node 20+', () => {
  assert.equal(isWasmGcSupported(), true);
});

test('createProcessor loads a SEP and newHost drives it', { skip: !haveWasm && 'capabilities wasm not built' }, async () => {
  const proc = await createProcessor(WASM);
  assert.equal(proc.env, 'node');

  const host = proc.newHost('CapHost');
  // DSL pipeline: map(times2) → filter > 0 → sink
  assert.equal(host.dsl(5), 10);
  // edge decoder: json string → re-injected typed Trade → typed handler
  assert.match(host.jsonDecode('{"symbol":"EURUSD","qty":100}'), /symbol=EURUSD/);
});

test('newHost throws a clear error for an unknown class', { skip: !haveWasm && 'capabilities wasm not built' }, async () => {
  const proc = await createProcessor(WASM);
  assert.throws(() => proc.newHost('NoSuchHost'), /exported class not found/);
});

test('jsonBridge drives any SEP with plain objects — JSON in, obj out', { skip: !haveWasm && 'capabilities wasm not built' }, async () => {
  const proc = await createProcessor(WASM);
  const sep = proc.jsonBridge('JsonHost');

  // pull: register a sink, send a JSON object, read it back parsed
  sep.track('tradeOut');
  sep.onEvent({ type: 'json', symbol: 'EURUSD', qty: 100 });
  const pulled = sep.query('tradeOut');
  assert.deepEqual(pulled, { symbol: 'EURUSD', qty: 100 });

  // push: subscribe drives a callback (the UI pattern)
  let pushed = null;
  sep.subscribe('tradeOut', (v) => { pushed = v; });
  sep.onEvent({ type: 'json', symbol: 'GBPUSD', qty: 250 });
  assert.deepEqual(pushed, { symbol: 'GBPUSD', qty: 250 });

  // explicit (type, payload) ingress + audit observability
  sep.track('trades');
  sep.send('trade', 'EURUSD:100');
  assert.match(String(sep.query('trades')), /symbol=EURUSD/);
  assert.ok(sep.audit().length > 0);
});

test('bench reports measured throughput, never a claimed figure', { skip: !haveWasm && 'capabilities wasm not built' }, async () => {
  const proc = await createProcessor(WASM);
  const host = proc.newHost('CapHost');
  const r = bench((i) => host.dsl(i + 1), { events: 5_000, warmup: 500, percentiles: true });
  assert.equal(r.events, 5_000);
  assert.ok(r.eventsPerSecond > 0);
  assert.ok(r.p50Nanos >= 0);
  assert.ok(typeof r.device === 'string' && r.device.length > 0);
});
