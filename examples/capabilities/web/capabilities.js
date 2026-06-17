// Capability definitions — pure data + run logic, no DOM (so it's testable headless
// and reused by app.js). Each runnable capability has a short Java/JS preview, a
// run(host, bridge) that executes live in WASM, and a fuller javaFull/jsFull for the
// "Show full example" expand.
//
// ONE PATH FIRST. Almost everything is driven through the generic JSON bridge
// (`sep` = proc.jsonBridge('JsonHost')): send events as plain objects, read named
// sinks back as objects. No bespoke @JSExport verb per capability — the graph does
// the typing (decoder nodes), the bridge is reused unchanged. The handful of things
// JSON genuinely cannot express (typed Java service handles, graph-control calls)
// are grouped separately and use a typed host (`host` = proc.newHost('CapHost')).

export const GETTING_STARTED = {
  build:
`# build the wasm (Java 21) — compiles the SEP + @JSExport hosts, copies into web/
mvn clean install
# serve it (must be http, not file://)
cd web && python3 -m http.server 8000   # → http://localhost:8000`,
  js:
`// load the TeaVM runtime loader first (sets globalThis.TeaVM)
//   <script src="./classes.wasm-runtime.js"></script>
import { createProcessor } from './fluxtion-wasm-runtime.js';

const proc = await createProcessor('./classes.wasm');   // load the compiled SEP

// THE primary handle: the generic JSON bridge — drive ANY SEP with plain objects
const sep = proc.jsonBridge('JsonHost');
//   sep.onEvent({ type:'trade', ... })   ingress (type routes it to a graph decoder)
//   sep.track(id); sep.query(id)         egress: read a named sink (pull)
//   sep.subscribe(id, cb)                egress: push each sink value (UI)
//   sep.signal / sep.audit / sep.get     signals, audit, flow state

// only for the few capabilities that exchange typed Java objects / control the graph:
const host = proc.newHost('CapHost');`,
  java:
`// Two exported hosts share one wasm (see ExportMain @JSExportClasses):
//
// 1. JsonHost — the GENERIC bridge. ONE fixed shell, reused for every SEP; it just
//    delegates to the reusable JsonBridgeHost(DataFlow). You write NO per-capability
//    Java for it — the graph's decoder nodes do the typing.
@JSExportClasses({CapHost.class, JsonHost.class})
public class ExportMain { public static void main(String[] a) {} }

public class JsonHost {
    private final JsonBridgeHost bridge;
    @JSExport public JsonHost() { bridge = new JsonBridgeHost(initSep()); }  // init the SEP
    // onEventJson / onEvent / addSink / lastSink / subscribe / getStreamed / signal / audit
}

// 2. CapHost — a TYPED host, only for service handles / graph control (Group B below).`,
};

export const CAPABILITIES = [
  {
    group: 'Example app — everything together',
  },
  {
    key: 'orderDesk',
    label: 'Live Order Desk — the full app',
    what:
      'A small but complete app built on the bridge: order/price events in, a risk gate ' +
      'that reads + stores position via a JS-backed service, accepted/rejected/marketData ' +
      'named sinks driving the UI, and a live audit log — everything on this page working ' +
      'together. Run sends one risk-checked order; open the full app for the interactive desk.',
    href: './app-orderdesk.html',
    linkText: 'Open the Live Order Desk app →',
    java:
`// the app host = the generic bridge + ONE typed service wiring
public class OrderDeskHost {
    @JSExport public void wirePositions(PositionReader r, PositionWriter w) {
        sep.registerService(new Service<>(new PositionBook() {
            public int position(String s)     { return r.position(s); }  // read browser state
            public void store(String s, int p) { w.store(s, p); }         // store browser state
        }, PositionBook.class));
    }
    // onEventJson / subscribe / lastSink / drainAudit — delegated to JsonBridgeHost
}

// the risk gate (in the graph): reads/stores position, re-injects a typed result
@OnEventHandler(filterString = "order")
public boolean onOrder(StringEvent e) {
    int next = book.position(sym) + signed;                 // read
    if (Math.abs(next) > 1000)
        dispatcher.processReentrantEvent(new Rejected(sym, qty, "limit breached"));
    else { book.store(sym, next);                           // store
           dispatcher.processReentrantEvent(new Accepted(sym, signed, next)); }
    return false;
}`,
    javaFull:
`// See the running app's "How it's built" tabs for every file. The shape:
//   OrderDeskHost  = JsonBridgeHost (events/sinks/audit) + wirePositions(service)
//   OrderRiskNode  = reads/stores position via PositionBook, re-injects Accepted/Rejected
//   PriceNode      = re-injects PriceTick
//   DSL flows      = subscribe(Accepted|Rejected|PriceTick).map(toJson).sink("accepted"|...)
//   PositionBook   = a plain Java service, implemented in JS at runtime (two @JSFunctors)

@OnEventHandler(filterString = "order")
public boolean onOrder(StringEvent e) {
    String sym = MiniJson.string(e.payload(), "symbol");
    int qty    = MiniJson.intField(e.payload(), "qty", 0);
    int signed = "sell".equals(MiniJson.string(e.payload(), "side")) ? -qty : qty;
    int next   = book.position(sym) + signed;
    if (qty <= 0)
        dispatcher.processReentrantEvent(new Rejected(sym, qty, "qty must be > 0"));
    else if (Math.abs(next) > 1000)
        dispatcher.processReentrantEvent(new Rejected(sym, qty, "position limit breached: " + next));
    else {
        book.store(sym, next);
        dispatcher.processReentrantEvent(new Accepted(sym, signed, next));
    }
    return false;
}`,
    js:
`const desk = proc.jsonBridge('OrderDeskHost');
desk.host.wirePositions((s) => positions[s] || 0, (s, p) => { positions[s] = p; });
desk.subscribe('accepted', (o) => renderRow(o));   // sink → UI
desk.onEvent({ type: 'order', symbol: 'EURUSD', side: 'buy', qty: 100 });`,
    jsFull:
`import { createProcessor } from '@telamin/fluxtion-wasm-runtime';
const desk = (await createProcessor('./classes.wasm')).jsonBridge('OrderDeskHost');

// the position book is BROWSER state, read + stored by the SEP via a JS-backed service
const positions = {};
desk.host.wirePositions(
  (sym) => positions[sym] || 0,                              // read
  (sym, pos) => { positions[sym] = pos; renderPositions(); } // store
);

// named sinks → UI
desk.subscribe('marketData', (t) => showTick(t));
desk.subscribe('accepted',   (o) => addRow('acc', o));
desk.subscribe('rejected',   (o) => addRow('rej', o));

// events in as plain objects
desk.onEvent({ type: 'order', symbol: 'EURUSD', side: 'buy', qty: 100 });
desk.onEvent({ type: 'price', symbol: 'EURUSD', price: 1.0850 });
console.log(desk.audit());   // the full node-by-node trail`,
    run: (host, bridge, desk) => {
      desk.track('accepted');
      desk.onEvent({ type: 'order', symbol: 'EURUSD', side: 'buy', qty: 100 });
      const a = desk.query('accepted');
      return 'onEvent({type:"order",symbol:"EURUSD",side:"buy",qty:100}) → query("accepted") = '
        + JSON.stringify(a) + '   (risk-checked; position stored via the JS service)';
    },
    wide: true,
  },
  {
    group: 'Driven by the JSON bridge — one API for every SEP',
  },
  {
    key: 'jsonBridge',
    label: 'The bridge — plain objects in, objects out',
    what:
      'The standard path. A single reusable JsonHost shell (no bespoke @JSExport verb ' +
      'per app) lets JavaScript drive any SEP with plain objects. onEvent({type,...}) ' +
      'routes by type to the graph\'s decoder; named sinks come back as objects via ' +
      'query (pull) or subscribe (push). Same JSON in → typed graph → JSON out, reused ' +
      'unchanged across every SEP. Wrapped by @telamin/fluxtion-wasm-runtime as ' +
      'proc.jsonBridge(\'JsonHost\'). Every card in this group uses it.',
    java:
`// You write the GRAPH (decoder + a typed→json sink); the bridge needs no per-app code.
public class JsonDecoderNode {                 // edge decoder, types the JSON
    @Inject @NoTriggerReference EventDispatcher dispatcher;
    @OnEventHandler(filterString = "json")
    public boolean onJson(StringEvent e) {
        dispatcher.processReentrantEvent(new Trade(field(e,"symbol"), parseInt(field(e,"qty"))));
        return false;
    }
}
DataFlowBuilder.subscribe(Trade.class).map(CapFuncs::tradeToJson).sink("tradeOut");`,
    javaFull:
`// 1. the reusable, SEP-agnostic bridge (holds a DataFlow — every generated SEP is one)
public final class JsonBridgeHost {
    private final DataFlow sep;
    public JsonBridgeHost(DataFlow sep) {
        this.sep = sep;
        sep.onEvent(new EventLogControlEvent(r -> audit.add(r.toString())));  // audit tap
    }
    public void onEventJson(String json) {                 // ingress: "type" routes it
        String type = MiniJson.string(json, "type");
        sep.onEvent(new StringEvent(type, json));          // a graph decoder types it
    }
    public void subscribe(String sinkId, SinkListener cb) { // push egress → JS callback
        sep.addSink(sinkId, (Object v) -> cb.onValue(String.valueOf(v)));
    }
    public void addSink(String id) { /* capture latest */ }
    public String lastSink(String id) { /* return latest as string */ }
}

// 2. the fixed @JSExport shell — the ONLY per-SEP line is the processor name
public class JsonHost {
    private final JsonBridgeHost bridge;
    @JSExport public JsonHost() { bridge = new JsonBridgeHost(initSep()); }
    private static DataFlow initSep() { var p = new CapabilitiesProcessor(); p.init(); return p; }
    @JSExport public void onEventJson(String j)            { bridge.onEventJson(j); }
    @JSExport public void addSink(String id)              { bridge.addSink(id); }
    @JSExport public String lastSink(String id)           { return bridge.lastSink(id); }
    @JSExport public void subscribe(String id, SinkListener cb) { bridge.subscribe(id, cb); }
    // + onEvent / getStreamed / signal / drainAudit / setLogLevel
}`,
    js:
`sep.track('tradeOut');
sep.onEvent({ type: 'json', symbol: 'EURUSD', qty: 100 });
sep.query('tradeOut')   // → { symbol: 'EURUSD', qty: 100 }`,
    jsFull:
`import { createProcessor } from '@telamin/fluxtion-wasm-runtime';
const proc = await createProcessor('./classes.wasm');
const sep  = proc.jsonBridge('JsonHost');   // one wrapper, any SEP

// egress (pull) — register the sink, send a plain object (its \`type\` routes it),
// then read the sink's latest value, JSON-parsed
sep.track('tradeOut');
sep.onEvent({ type: 'json', symbol: 'EURUSD', qty: 100 });
sep.query('tradeOut');               // → { symbol: 'EURUSD', qty: 100 }   (matches Run)

// egress (push) — a sink value becomes a UI update
sep.subscribe('tradeOut', (trade) => renderRow(trade));
sep.onEvent({ type: 'json', symbol: 'GBPUSD', qty: 250 });   // renderRow({ symbol: 'GBPUSD', qty: 250 })

sep.signal('greet', 'world');        // signals
sep.setLogLevel('DEBUG');            // retune audit at runtime
sep.audit();                         // drain audit records`,
    run: (host, sep) => {
      sep.track('tradeOut');
      sep.onEvent({ type: 'json', symbol: 'EURUSD', qty: 100 });
      return 'sep.onEvent({type:"json",symbol:"EURUSD",qty:100}) → sep.query("tradeOut") = '
        + JSON.stringify(sep.query('tradeOut')) + '  (a JS object, no bespoke verb)';
    },
    wide: true,
  },
  {
    key: 'sink',
    label: 'Sinks — pull (query) and push (subscribe)',
    what:
      'A sink is how values leave the graph. The bridge reads ANY named sink the graph ' +
      'declares two ways: query(id) pulls the latest value, subscribe(id, cb) pushes ' +
      'every value to a callback (a @JSFunctor under the hood) — so a sink value becomes ' +
      'a DOM update. No per-sink Java; the graph just names sinks with .sink("id").',
    java:
`// the graph names sinks; the bridge reads them all generically
DataFlowBuilder.subscribe(Integer.class)
        .map(CapFuncs::times2).filter(CapFuncs::positive).sink("dslOut");`,
    javaFull:
`// Java NAMES sinks in the DSL (.sink("id")); the bridge captures any of them.
// Pull: addSink(id) starts capturing, lastSink(id) returns the latest value.
// Push: subscribe(id, jsFn) wires the value straight to a JS callback (@JSFunctor),
//       which is how "a value reached a sink" becomes "update this DOM element".
public void subscribe(String sinkId, SinkListener listener) {   // in JsonBridgeHost
    sep.addSink(sinkId, (Object v) -> listener.onValue(String.valueOf(v)));
}`,
    js:
`sep.track('dslOut');
sep.onEvent({ type:'number', value:8 });   sep.query('dslOut');   // 16   (pull)
sep.subscribe('dslOut', v => render(v));    sep.onEvent({type:'number', value:9}); // push 18`,
    jsFull:
`// pull
sep.track('dslOut');
sep.onEvent({ type:'number', value:8 });
console.log(sep.query('dslOut'));            // 16   (8 → ×2)

// push — the UI pattern: each sink value drives a callback
sep.subscribe('dslOut', (v) => document.querySelector('#out').textContent = v);
sep.onEvent({ type:'number', value:9 });     // callback fires with 18`,
    run: (host, sep) => {
      sep.track('dslOut');
      sep.onEvent({ type: 'number', value: 8 });
      const pulled = sep.query('dslOut');
      let pushed = null;
      sep.subscribe('dslOut', (v) => { pushed = v; });
      sep.onEvent({ type: 'number', value: 9 });
      return `pull  query("dslOut")        = ${pulled}   (8 → ×2 → 16)\n`
           + `push  subscribe("dslOut") got = ${pushed}   (9 → ×2 → 18)`;
    },
    wide: true,
  },
  {
    key: 'dsl',
    label: 'DSL pipeline — map / filter',
    what:
      'A Fluxtion DSL flow: subscribe(Integer) → map(×2) → filter(>0) → sink. The ' +
      'map/filter method references resolve at build time (closed-world), so the compiled ' +
      'processor does no reflection at runtime. Driven through the bridge: a "number" ' +
      'decoder node re-injects a typed Integer, exactly like the json decoder.',
    java:
`// the graph (generated AOT) — plain statics, closed-world safe
DataFlowBuilder.subscribe(Integer.class)
        .map(CapFuncs::times2)        // x -> 2x
        .filter(CapFuncs::positive)   // keep 2x > 0
        .sink("dslOut");

// a "number" decoder re-injects a typed Integer (so the bridge can reach this flow)
@OnEventHandler(filterString = "number")
public boolean onNumber(StringEvent e) {
    dispatcher.processReentrantEvent(MiniJson.intField(e.payload(), "value", 0));
    return false;
}`,
    javaFull:
`// 1. the functions (plain statics — closed-world safe, no SerializedLambda)
public final class CapFuncs {
    public static Integer times2(Integer x) { return x * 2; }
    public static boolean positive(Integer x) { return x > 0; }
}

// 2. the DSL graph (in GenerateCapabilities, the build-time generator)
DataFlowBuilder.subscribe(Integer.class)
        .map(CapFuncs::times2)
        .filter(CapFuncs::positive)
        .sink("dslOut");

// 3. an edge decoder so {"type":"number","value":N} reaches the Integer flow —
//    the same re-injection pattern as the json decoder, for a primitive.
public class NumberDecoderNode {
    @Inject @NoTriggerReference EventDispatcher dispatcher;
    @OnEventHandler(filterString = "number")
    public boolean onNumber(StringEvent e) {
        dispatcher.processReentrantEvent(MiniJson.intField(e.payload(), "value", 0));
        return false;
    }
}
// → no bespoke host method: the generic JsonHost drives it.`,
    js:
`sep.track('dslOut');
sep.onEvent({ type:'number', value:5 });    sep.query('dslOut');   // 10  (5 → ×2)
sep.onEvent({ type:'number', value:-4 });   // -8 filtered → dslOut unchanged`,
    jsFull:
`sep.track('dslOut');
sep.onEvent({ type:'number', value:5 });
console.log(sep.query('dslOut'));    // 10   (5 → ×2 → 10, passes >0)

sep.onEvent({ type:'number', value:-4 });
console.log(sep.query('dslOut'));    // still 10 — -8 was filtered out (no new sink value)`,
    run: (host, sep) => {
      sep.track('dslOut');
      sep.onEvent({ type: 'number', value: 5 });
      const v1 = sep.query('dslOut');
      sep.onEvent({ type: 'number', value: -4 });
      const v2 = sep.query('dslOut');
      return `onEvent({type:"number",value:5})  → query("dslOut") = ${v1}   (5 → ×2 → 10)\n`
           + `onEvent({type:"number",value:-4}) → -8 filtered, dslOut unchanged = ${v2}`;
    },
    wide: true,
  },
  {
    key: 'getStreamed',
    label: 'getStreamed — read a flow’s state',
    what:
      'Read the current value of any flow node by id. The flow is tagged .id("doubled"); ' +
      'sep.get("doubled") resolves it through a map-based node lookup (not reflection), so ' +
      'it works in WASM. Pull-style state read, no sink required.',
    java:
`DataFlowBuilder.subscribe(Integer.class)
        .map(CapFuncs::times2)
        .id("doubled");               // name the flow so it can be read back`,
    javaFull:
`// name a flow with .id(...) so it can be read back by id
DataFlowBuilder.subscribe(Integer.class)
        .map(CapFuncs::times2)
        .id("doubled");

// the bridge exposes it: getStreamed -> getNodeById -> map-based nodeNameLookup
// (no reflection in WASM). Returns the flow's current value as a string.
public String getStreamed(String flowId) {   // in JsonBridgeHost
    Object v = sep.getStreamed(flowId);
    return v == null ? null : String.valueOf(v);
}`,
    js:
`sep.onEvent({ type:'number', value:7 });
sep.get('doubled')   // 14   (7 × 2, read from the flow)`,
    jsFull:
`sep.onEvent({ type:'number', value:7 });
console.log(sep.get('doubled'));   // 14   (7 × 2, read straight from the flow node)`,
    run: (host, sep) => {
      sep.onEvent({ type: 'number', value: 7 });
      return `onEvent({type:"number",value:7}) → get("doubled") = ${sep.get('doubled')}   (7 × 2)`;
    },
  },
  {
    key: 'signal',
    label: 'Signals — publishSignal',
    what:
      'Inject a named control signal into the graph. A DSL subscription listens for the ' +
      '"greet" signal and forwards its value to a sink. Signals are just events, dispatched ' +
      'by type — so sep.signal(name, value) works in WASM; read the result from a sink.',
    java:
`DataFlowBuilder.subscribeToSignal("greet", String.class)
        .sink("signalOut");`,
    javaFull:
`// a subscription that listens for the "greet" signal and forwards its value to a sink
DataFlowBuilder.subscribeToSignal("greet", String.class)
        .sink("signalOut");

// the bridge forwards a named signal (no per-signal Java):
public void signal(String name, String value) { sep.publishSignal(name, value); }`,
    js:
`sep.track('signalOut');
sep.signal('greet', 'world');
sep.query('signalOut')   // "world"`,
    jsFull:
`sep.track('signalOut');
sep.signal('greet', 'world');               // Signal<String> with filter "greet"
console.log(sep.query('signalOut'));        // "world"  (received by the subscription)`,
    run: (host, sep) => {
      sep.track('signalOut');
      sep.signal('greet', 'world');
      return `signal("greet","world") → query("signalOut") = ${sep.query('signalOut')}`;
    },
  },
  {
    key: 'stringEvents',
    label: 'Typed events → named sinks (routed by type)',
    what:
      'Many event types, one ingress, no per-event Java. sep.onEvent({type, ...}) (or ' +
      'sep.send(type, payload)) routes by type to the matching converter/decoder in the ' +
      'graph, which decodes the payload (explicit code — no reflection) and emits to its ' +
      'own named sink. Add as many types/sinks as you like; the bridge reads them all.',
    java:
`// converter flows IN the graph, routed by the event's type (filterString)
DataFlowBuilder.subscribe(StringEvent.class)
        .filter(CapFuncs::isTrade).map(CapFuncs::toTrade).sink("trades");
DataFlowBuilder.subscribe(StringEvent.class)
        .filter(CapFuncs::isQuote).map(CapFuncs::toQuote).sink("quotes");`,
    javaFull:
`import com.telamin.fluxtion.runtime.event.Event;

// 1. a generic typed event — filterString() is the routing key
public final class StringEvent implements Event {
    private final String filter, payload;
    public StringEvent(String filter, String payload) { this.filter = filter; this.payload = payload; }
    @Override public String filterString() { return filter; }
    public String payload() { return payload; }
}

// 2. converter flows — one per type; decode is explicit (split/parseInt, no reflection)
DataFlowBuilder.subscribe(StringEvent.class)
        .filter(CapFuncs::isTrade).map(CapFuncs::toTrade).sink("trades");
DataFlowBuilder.subscribe(StringEvent.class)
        .filter(CapFuncs::isQuote).map(CapFuncs::toQuote).sink("quotes");

// 3. no bespoke host method — the bridge sends the typed event and reads the sink:
//      sep.send("trade", "EURUSD:100");  sep.query("trades");`,
    js:
`sep.track('trades');  sep.track('quotes');
sep.send('trade', 'EURUSD:100');    sep.query('trades');   // "Trade{symbol=EURUSD, qty=100}"
sep.send('quote', 'EURUSD:1.08');   sep.query('quotes');   // "Quote{symbol=EURUSD, price=1.08}"`,
    jsFull:
`sep.track('trades');
sep.track('quotes');

sep.send('trade', 'EURUSD:100');
console.log(sep.query('trades'));    // "Trade{symbol=EURUSD, qty=100}"

sep.send('quote', 'EURUSD:1.08');
console.log(sep.query('quotes'));    // "Quote{symbol=EURUSD, price=1.08}"
// same two calls for any type/sink the graph declares — no per-event @JSExport`,
    run: (host, sep) => {
      sep.track('trades');
      sep.track('quotes');
      sep.send('trade', 'EURUSD:100');
      sep.send('quote', 'EURUSD:1.08');
      return `send("trade","EURUSD:100")  → query("trades") = ${sep.query('trades')}\n`
           + `send("quote","EURUSD:1.08") → query("quotes") = ${sep.query('quotes')}`;
    },
    wide: true,
  },
  {
    key: 'jsonDecode',
    label: 'Edge decoder → re-entrant typed event (the graph pattern)',
    what:
      'How the bridge keeps the graph typed. A decoder node at the EDGE handles a typed ' +
      'event, decodes it, and RE-INJECTS a strongly-typed object via the EventDispatcher ' +
      '(processReentrantEvent). Downstream nodes handle the typed object with a normal ' +
      '@OnEventHandler — no string/JSON handling leaks past the edge. This is the pattern ' +
      'that lets onEvent({type:...}) drive a fully-typed graph. The decoder gets the ' +
      'dispatcher via @Inject EventDispatcher (or DataFlowContextListener / a base node).',
    java:
`// decoder at the edge — the only node that knows about JSON
public class JsonDecoderNode {
    @Inject @NoTriggerReference public EventDispatcher eventDispatcher;
    @OnEventHandler(filterString = "json")
    public boolean onJson(StringEvent e) {
        Trade trade = new Trade(field(e,"symbol"), parseInt(field(e,"qty")));
        eventDispatcher.processReentrantEvent(trade);   // inject the TYPED event mid-cycle
        return false;                                   // raw string goes no further
    }
}
// downstream: a STANDARD typed handler — knows nothing about strings/JSON
DataFlowBuilder.subscribe(Trade.class).map(CapFuncs::tradeToJson).sink("tradeOut");`,
    javaFull:
`import com.telamin.fluxtion.runtime.callback.EventDispatcher;
import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;

public record Trade(String symbol, int qty) {}

// 1. the EDGE decoder — handles the json string, re-injects a typed Trade
public class JsonDecoderNode {
    @Inject @NoTriggerReference public EventDispatcher eventDispatcher;   // 3 ways to get this
    @OnEventHandler(filterString = "json")
    public boolean onJson(StringEvent e) {
        Trade trade = new Trade(field(e.payload(),"symbol"), Integer.parseInt(field(e.payload(),"qty")));
        eventDispatcher.processReentrantEvent(trade);   // ← dispatch the typed event into the graph
        return false;
    }
}

// 2. downstream stays standard: a typed flow maps Trade → JSON on a named sink
DataFlowBuilder.subscribe(Trade.class).map(CapFuncs::tradeToJson).sink("tradeOut");

// 3. nothing bespoke on the host — the generic bridge drives it:
//      sep.onEvent({ type:'json', symbol:'EURUSD', qty:100 });  sep.query('tradeOut');`,
    js:
`sep.track('tradeOut');
sep.onEvent({ type:'json', symbol:'EURUSD', qty:100 });
sep.query('tradeOut')   // → { symbol:'EURUSD', qty:100 }  (typed at the edge)`,
    jsFull:
`sep.track('tradeOut');
sep.onEvent({ type:'json', symbol:'EURUSD', qty:100 });
console.log(sep.query('tradeOut'));
// → { symbol:'EURUSD', qty:100 }
// only JsonDecoderNode saw the raw JSON; everything downstream handled a typed Trade`,
    run: (host, sep) => {
      sep.track('tradeOut');
      sep.onEvent({ type: 'json', symbol: 'EURUSD', qty: 100 });
      return `onEvent({type:"json",symbol:"EURUSD",qty:100}) → query("tradeOut") = `
        + JSON.stringify(sep.query('tradeOut')) + '   (typed, decoded at the edge)';
    },
    wide: true,
  },
  {
    key: 'audit',
    label: 'Audit logging',
    what:
      'Every node invocation is captured as a structured audit record (cfg.addEventAudit). ' +
      'The bridge taps the audit stream at construction; sep.audit() drains the records ' +
      'for what just happened. Uses the runtime EventLogManager — de-JUL’d in fluxtion ' +
      '1.0.8 (it pulled java.util.logging on ≤1.0.7), so it now compiles + runs in WASM.',
    java:
`// enable event audit in the generator (real EventLogManager, 1.0.8+)
cfg.addEventAudit(LogLevel.INFO);

// the bridge taps it once, at construction:
sep.onEvent(new EventLogControlEvent(record -> audit.add(record.toString())));`,
    javaFull:
`import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;

// 1. turn on event auditing in the generator — captures every node invocation
cfg.addEventAudit(LogLevel.INFO);            // real EventLogManager (de-JUL'd, 1.0.8+)

// 2. the bridge registers a LogRecordListener at construction (no per-app code):
public JsonBridgeHost(DataFlow sep) {
    this.sep = sep;
    sep.onEvent(new EventLogControlEvent(record -> audit.add(record.toString())));
}
public String drainAudit() { var s = String.join("\\n", audit); audit.clear(); return s; }

// 3. from JS: send any event, then read what it produced
//      sep.onEvent({ type:'number', value:5 });  sep.audit();`,
    js:
`sep.onEvent({ type:'number', value:5 });
sep.audit()   // the structured eventLogRecord(s) produced by that event`,
    jsFull:
`sep.onEvent({ type:'number', value:5 });
console.log(sep.audit());
// → eventLogRecord: ... one structured record per node invocation for that event`,
    run: (host, sep) => {
      sep.audit();   // clear prior
      sep.onEvent({ type: 'number', value: 5 });
      const lines = sep.audit().split('\n').filter(Boolean);
      return `onEvent({type:"number",value:5}) → sep.audit() captured ${lines.length} log lines:\n`
        + lines.slice(0, 8).join('\n') + (lines.length > 8 ? '\n…' : '');
    },
    wide: true,
  },
  {
    key: 'setLogLevel',
    label: 'Change log level at runtime',
    what:
      'Adjust the audit log level by publishing an EventLogControlEvent — an event, routed ' +
      'to the auditor, so it works in WASM. sep.setLogLevel("DEBUG") retunes verbosity with ' +
      'no reflection and no rebuild.',
    java:
`// the bridge publishes a control event (routed to the auditor) — not reflection
public void setLogLevel(String level) {
    sep.onEvent(new EventLogControlEvent(LogLevel.valueOf(level)));
}`,
    javaFull:
`import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;

// log level is changed by PUBLISHING an event (routed to the auditor) — no reflection
public void setLogLevel(String level) {      // in JsonBridgeHost
    sep.onEvent(new EventLogControlEvent(LogLevel.valueOf(level)));  // NONE|ERROR|WARN|INFO|DEBUG|TRACE
}`,
    js: `sep.setLogLevel('DEBUG')   // subsequent audit records captured at DEBUG`,
    jsFull:
`sep.setLogLevel('DEBUG');             // raise verbosity at runtime
sep.onEvent({ type:'number', value:5 });
console.log(sep.audit());             // now includes DEBUG-level detail`,
    run: (host, sep) => {
      sep.setLogLevel('DEBUG');
      return 'sep.setLogLevel("DEBUG") — audit now records at DEBUG '
        + '(publishes an EventLogControlEvent, routed to the auditor, no reflection)';
    },
  },

  {
    group: 'Typed host API — when JSON isn’t enough',
  },
  {
    key: 'exportedService',
    label: 'Exported services (@ExportService)',
    what:
      'Why a typed host: this exchanges a typed Java handle, not an event. A node implements ' +
      'an interface marked @ExportService; the host calls it directly via ' +
      'getExportedService(...). The dispatch is generated code (no reflection), so it works ' +
      'in WASM — but the call signature is Java, so it lives on CapHost, not the JSON bridge.',
    java:
`public class CalculatorNode implements @ExportService Calculator {
    private int total;
    @Override public void add(int a, int b) { total = a + b; }
    public int getTotal() { return total; }   // read via getNodeById
}

@JSExport public String exportedService(int a, int b) {
    Calculator calc = sep.getExportedService(Calculator.class);
    calc.add(a, b);                                   // the typed API call
    CalculatorNode node = sep.getNodeById("calc");
    return "add(" + a + "," + b + ") -> total=" + node.getTotal();
}`,
    javaFull:
`import com.telamin.fluxtion.runtime.annotations.ExportService;

// 1. the exported interface — methods are dispatch (void/boolean), not queries
public interface Calculator { void add(int a, int b); }

// 2. a node implements it; the SEP exposes it (generated dispatch, no reflection)
public class CalculatorNode implements @ExportService Calculator {
    private int total;
    @Override public void add(int a, int b) { total = a + b; }
    public int getTotal() { return total; }
}
// wire it in: cfg.addNode(new CalculatorNode(), "calc");

// 3. host calls the exported service directly; read the effect separately
@JSExport public String exportedService(int a, int b) {
    Calculator calc = sep.getExportedService(Calculator.class);
    calc.add(a, b);
    CalculatorNode node = sep.getNodeById("calc");
    return "add(" + a + "," + b + ") -> total=" + node.getTotal();
}`,
    js: `host.exportedService(3, 4)`,
    jsFull: `console.log(host.exportedService(3, 4));   // "add(3,4) -> total=7"`,
    run: (host) => `host.exportedService(3, 4)  →  ${host.exportedService(3, 4)}`,
  },
  {
    key: 'injectedService',
    label: 'Injected services (@ServiceRegistered)',
    what:
      'Why a typed host: it registers a typed Java service and relies on reflective wiring. ' +
      'A @ServiceRegistered method is wired by ServiceRegistryNode via reflection — which ' +
      'needs metadata in WASM, supplied by a TeaVM ReflectionSupplier (the WASM twin of ' +
      'GraalVM reflect-config). This page registers GreeterConsumer.wire, so injection fires ' +
      '→ "hello world". Without the supplier the reflective scan silently no-ops.',
    java:
`public class GreeterConsumer {
    private Greeter greeter;
    @ServiceRegistered                       // reflective wiring — needs a ReflectionSupplier in WASM
    public void wire(Greeter greeter, String name) { this.greeter = greeter; }
    public String greetVia(String n) { return greeter == null ? "NOT_INJECTED" : greeter.greet(n); }
}

@JSExport public String injectedService(String name) {
    sep.registerService(new Service<>(n -> "hello " + n, Greeter.class));
    return ((GreeterConsumer) sep.getNodeById("greeterConsumer")).greetVia(name);
}`,
    javaFull:
`import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.service.Service;

public interface Greeter { String greet(String name); }

public class GreeterConsumer {
    private Greeter greeter;
    @ServiceRegistered                       // wired by ServiceRegistryNode via reflection
    public void wire(Greeter greeter, String name) { this.greeter = greeter; }
    public String greetVia(String n) { return greeter == null ? "NOT_INJECTED" : greeter.greet(n); }
}
// wire it in: cfg.addNode(new GreeterConsumer(), "greeterConsumer");

// THE KEY: make wire() reflectable in WASM via a TeaVM ReflectionSupplier (build-time
// SPI in META-INF/services/org.teavm.classlib.ReflectionSupplier). In production the
// generator emits this for every @ServiceRegistered class — like reflect-config.json.
public class CapReflectionSupplier implements ReflectionSupplier {
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext c, String cn) {
        return "com.telamin.fluxtion.wasm.cap.GreeterConsumer".equals(cn)
            ? List.of(new MethodDescriptor("wire", Greeter.class, String.class, void.class))
            : List.of();
    }
}

@JSExport public String injectedService(String name) {
    sep.registerService(new Service<>(n -> "hello " + n, Greeter.class));
    return ((GreeterConsumer) sep.getNodeById("greeterConsumer")).greetVia(name);
}`,
    js: `host.injectedService("world")  // "hello world" (with the ReflectionSupplier)`,
    jsFull:
`// works because GreeterConsumer.wire is registered reflectable (see Java tab).
// Without the ReflectionSupplier, scanNode's reflection silently no-ops and this
// would return NOT_INJECTED — then use callback-via-event (next card) instead.
console.log(host.injectedService("world"));   // "hello world"`,
    run: (host) => host.injectedService('world'),
  },
  {
    key: 'callbackViaEvent',
    label: 'Service/callback delivered as an event',
    what:
      'Why a typed host: the event carries a Java callback object. The WASM-friendly ' +
      'alternative to @ServiceRegistered — carry the callback IN an event and let a normal ' +
      '@OnEventHandler capture + invoke it. Plain dispatch + a direct call, no reflection. ' +
      'The callback is a Java lambda, so it is handed in through a typed host method.',
    java:
`public record ServiceEvent(Greeter greeter, String name) {}

public class CallbackReceiver {
    private String last = "NONE";
    @OnEventHandler                          // plain dispatch — works in WASM
    public boolean onService(ServiceEvent e) { last = e.greeter().greet(e.name()); return false; }
    public String last() { return last; }
}

@JSExport public String callbackViaEvent(String name) {
    sep.onEvent(new ServiceEvent(n -> "hi " + n, name));
    return ((CallbackReceiver) sep.getNodeById("callbackReceiver")).last();
}`,
    javaFull:
`import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

// carry the service/callback IN an event; a normal handler captures + invokes it
public record ServiceEvent(Greeter greeter, String name) {}

public class CallbackReceiver {
    private String last = "NONE";
    @OnEventHandler                          // instanceof dispatch — works in WASM
    public boolean onService(ServiceEvent e) {
        last = e.greeter() == null ? "NULL" : e.greeter().greet(e.name());
        return false;
    }
    public String last() { return last; }
}
// wire it in: cfg.addNode(new CallbackReceiver(), "callbackReceiver");

@JSExport public String callbackViaEvent(String name) {
    sep.onEvent(new ServiceEvent(n -> "hi " + n, name));   // no reflective registry needed
    return ((CallbackReceiver) sep.getNodeById("callbackReceiver")).last();
}`,
    js: `host.callbackViaEvent("world")`,
    jsFull: `console.log(host.callbackViaEvent("world"));   // "hi world"  (works where @ServiceRegistered fails)`,
    run: (host) => `host.callbackViaEvent("world")  →  ${host.callbackViaEvent('world')}`,
  },
  {
    key: 'jsService',
    label: 'JS-implemented service — query JS state from the SEP',
    what:
      'Why a typed host: a JS function is injected as a typed Java service. Via TeaVM ' +
      '@JSFunctor a plain JS function implements a Java interface; a node calls it during ' +
      'processing — reaching back into the JS context (a live rate table, a cache, browser ' +
      'state) from inside the compiled SEP. Bidirectional: JS→WASM (the function) + WASM→JS ' +
      '(the call). Synchronous only — model async JS data as events instead.',
    java:
`public interface PriceStore { int priceFor(String symbol); }              // the node's service
@JSFunctor public interface JsPriceStore extends JSObject {               // a JS fn implements this
    int priceFor(String symbol);
}
public class PriceLookupNode {
    @ServiceRegistered public void wirePrices(PriceStore p, String n) { prices = p; }
    @OnEventHandler public boolean onSymbol(SymbolEvent e) {
        lastPrice = prices.priceFor(e.symbol());   // ← calls back into JS
        return true;
    }
}
@JSExport public String priceLookup(JsPriceStore js, String symbol) {
    sep.registerService(new Service<>(js::priceFor, PriceStore.class));   // @JSFunctor → PriceStore
    sep.onEvent(new SymbolEvent(symbol));
    return symbol + " = " + node.lastPrice();
}`,
    javaFull:
`import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

// 1. the service the SEP node uses — plain Java, knows nothing about JS
public interface PriceStore { int priceFor(String symbol); }

// 2. JSO view a plain JS function can implement (single method → @JSFunctor)
@JSFunctor public interface JsPriceStore extends JSObject { int priceFor(String symbol); }

// 3. a node receives the injected service and calls it during processing
public class PriceLookupNode {
    private PriceStore prices; private int lastPrice = -1;
    @ServiceRegistered public void wirePrices(PriceStore prices, String name) { this.prices = prices; }
    @OnEventHandler public boolean onSymbol(SymbolEvent e) {
        lastPrice = prices == null ? -1 : prices.priceFor(e.symbol());   // ← into JS
        return true;
    }
    public int lastPrice() { return lastPrice; }
}
// wire it in: cfg.addNode(new PriceLookupNode(), "priceLookup");
// + register wirePrices reflectable (CapReflectionSupplier — see Injected services)

// 4. host: adapt the JS functor to the Java service, register, drive
@JSExport public String priceLookup(JsPriceStore jsPrices, String symbol) {
    sep.registerService(new Service<>(jsPrices::priceFor, PriceStore.class));
    sep.onEvent(new SymbolEvent(symbol));
    return symbol + " = " + ((PriceLookupNode) sep.getNodeById("priceLookup")).lastPrice();
}`,
    js: `host.priceLookup(sym => liveRates[sym], "EURUSD")`,
    jsFull:
`const liveRates = { EURUSD: 108, GBPUSD: 127 };       // state that lives in JS
// the JS function IS the PriceStore service; the SEP node calls it during processing
console.log(host.priceLookup(sym => liveRates[sym], "EURUSD"));
// → "EURUSD = 108  (from the JS price function)"`,
    run: (host) => host.priceLookup((sym) => ({ EURUSD: 108, GBPUSD: 127 }[sym] ?? 0), 'EURUSD'),
    wide: true,
  },
  {
    key: 'triggerCalc',
    label: 'triggerCalculation — fire the graph',
    what:
      'Why a typed host: a graph-control call, not an event. Manually trigger a calculation ' +
      'cycle without an inbound event (e.g. a timer tick from JS). It is a method on the SEP, ' +
      'so it is exposed via a typed host method.',
    java:
`@JSExport public String triggerCalc() {
    sep.triggerCalculation();
    return "triggerCalculation() returned";
}`,
    javaFull:
`// fire a calculation cycle without an inbound event (e.g. a timer tick from JS)
@JSExport public String triggerCalc() {
    sep.triggerCalculation();
    return "triggerCalculation() returned";
}`,
    js: `host.triggerCalc()`,
    jsFull: `console.log(host.triggerCalc());   // "triggerCalculation() returned"`,
    run: (host) => host.triggerCalc(),
  },

  {
    group: 'Not supported in WASM (by design / known gap)',
  },
  {
    key: 'connectors',
    label: 'Data connectors / event feeds / threads',
    what:
      'Agrona-backed connectors, event feeds and agent threads are host-side integration — ' +
      'supplied by the host runtime, never compiled into the SEP. In WASM the host calls ' +
      'onEvent directly; there is nothing to connect.',
    note: true,
  },
  {
    key: 'forked',
    label: 'Parallel / forked triggers (ForkJoin)',
    what: 'WASM is single-threaded; ForkJoin-based parallel trigger execution has no equivalent.',
    note: true,
  },
];
