// WASM capability probe runner. Mirrors the capability page's two paths and prints
// ✅/❌ — a throw means the capability is unsupported in WASM (record the error).
// Loads the SEP through the productised @telamin/fluxtion-wasm-runtime package.
//   node probe/run-probes.mjs [wasmDir=../target/wasm-gc]
import { createProcessor } from '../../../runtime/src/index.mjs';

const dir = process.argv[2] || new URL('../target/wasm-gc', import.meta.url).pathname;
const proc = await createProcessor(`${dir}/classes.wasm`);
// Group A — the generic JSON bridge (the standard path); Group B — typed host handles
const sep = proc.jsonBridge('JsonHost');
const host = proc.newHost('CapHost');

function probe(name, action) {
  try {
    const r = action();
    console.log(`WASM OK   ${name} -> ${String(r).replace(/\n/g, ' | ').slice(0, 100)}`);
  } catch (e) {
    console.log(`WASM FAIL ${name} -> ${(e && e.message) || e}`);
  }
}

// ── Group A: driven by the JSON bridge (plain objects in, sinks out) ──
probe('bridge.dsl', () => { sep.track('dslOut'); sep.onEvent({ type: 'number', value: 5 }); return sep.query('dslOut'); });
probe('bridge.getStreamed', () => { sep.onEvent({ type: 'number', value: 7 }); return sep.get('doubled'); });
probe('bridge.signal', () => { sep.track('signalOut'); sep.signal('greet', 'world'); return sep.query('signalOut'); });
probe('bridge.stringEvent-trade', () => { sep.track('trades'); sep.send('trade', 'EURUSD:100'); return sep.query('trades'); });
probe('bridge.stringEvent-quote', () => { sep.track('quotes'); sep.send('quote', 'EURUSD:1.08'); return sep.query('quotes'); });
probe('bridge.jsonDecode', () => { sep.track('tradeOut'); sep.onEvent({ type: 'json', symbol: 'EURUSD', qty: 100 }); return JSON.stringify(sep.query('tradeOut')); });
let pushed = null;
probe('bridge.subscribe (push)', () => { sep.subscribe('dslOut', (v) => { pushed = v; }); sep.onEvent({ type: 'number', value: 9 }); return `pushed=${pushed}`; });
probe('bridge.audit', () => { sep.onEvent({ type: 'number', value: 5 }); return sep.audit().split('\n').filter(Boolean).length + ' log lines'; });
probe('bridge.setLogLevel', () => { sep.setLogLevel('TRACE'); return 'ok'; });

// ── Group B: typed host handles (exchange Java objects / control the graph) ──
probe('host.exportedService', () => host.exportedService(3, 4));
probe('host.injectedService', () => host.injectedService('world'));
probe('host.callbackViaEvent', () => host.callbackViaEvent('world'));
probe('host.priceLookup (JS service)', () => host.priceLookup((sym) => ({ EURUSD: 108, GBPUSD: 127 }[sym] ?? 0), 'EURUSD'));
probe('host.triggerCalc', () => host.triggerCalc());
