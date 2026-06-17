// Live Order Desk — drives the WASM SEP entirely through the generic JSON bridge.
// Orders + prices go in as plain objects; risk decisions, market data and the
// position book come back through named sinks. The position book is BROWSER state:
// the SEP reads and stores it via a JS-backed service (wirePositions).
import { createProcessor } from './fluxtion-wasm-runtime.js';
import { highlight } from './highlight.js';

const $ = (id) => document.getElementById(id);
const status = $('status');

// ── browser-held state the SEP reads + writes through the position service ──
const positions = {};          // symbol -> net position
const market = {};             // symbol -> last price

function fail(msg, err) {
  status.textContent = msg + (err ? ` — ${err.message || err}` : '');
  status.classList.add('err');
}

let desk;
try {
  const proc = await createProcessor('./classes.wasm');
  desk = proc.jsonBridge('OrderDeskHost');

  // the JS-backed PositionBook service: the SEP calls these DURING event processing
  desk.host.wirePositions(
    (sym) => positions[sym] || 0,                              // read browser state
    (sym, pos) => { positions[sym] = pos; renderPositions(); } // store browser state
  );

  // egress: each named sink pushes to a UI element
  desk.subscribe('marketData', (t) => { market[t.symbol] = t.price; renderMarket(); });
  desk.subscribe('accepted', (o) =>
    prepend('accepted', 'acc', `✓ ${o.symbol}  ${o.qty > 0 ? '+' : ''}${o.qty}  → net ${o.position}`));
  desk.subscribe('rejected', (o) =>
    prepend('rejected', 'rej', `✗ ${o.symbol}  ${o.qty}  — ${o.reason}`));

  status.textContent = 'Loaded — the order desk runs locally in WASM. Send events below.';
} catch (e) {
  fail('Failed to load WASM (needs a recent browser)', e);
  throw e;
}

// ── send events (and surface the audit trail each event produced) ──
function send(event) {
  desk.onEvent(event);
  showAudit(desk.audit());
}

$('sendOrder').onclick = () => send({
  type: 'order',
  symbol: $('symbol').value,
  side: $('side').value,
  qty: parseInt($('qty').value, 10) || 0,
});

$('sendPrice').onclick = () => send({
  type: 'price',
  symbol: $('symbol').value,
  price: parseFloat($('price').value) || 0,
});

// a small burst to make it lively — random orders + price ticks
$('rand').onclick = () => {
  const syms = ['EURUSD', 'GBPUSD', 'USDJPY'];
  const base = { EURUSD: 1.085, GBPUSD: 1.27, USDJPY: 156.8 };
  const pick = (i) => syms[(i * 7 + 3) % syms.length];   // deterministic-ish spread
  for (let i = 0; i < 8; i++) {
    const sym = pick(i);
    if (i % 3 === 0) {
      send({ type: 'price', symbol: sym, price: +(base[sym] * (1 + (i - 4) / 1000)).toFixed(4) });
    } else {
      send({ type: 'order', symbol: sym, side: i % 2 ? 'sell' : 'buy', qty: 50 * ((i % 4) + 1) });
    }
  }
};

// ── renderers ──
function renderPositions() {
  const tbody = $('positions');
  const syms = Object.keys(positions).sort();
  if (!syms.length) { tbody.innerHTML = '<tr><td colspan="2" style="color:var(--muted)">none yet</td></tr>'; return; }
  tbody.innerHTML = syms.map((s) => {
    const p = positions[s];
    const cls = p > 0 ? 'pos-long' : p < 0 ? 'pos-short' : '';
    return `<tr><td>${s}</td><td class="num ${cls}">${p > 0 ? '+' : ''}${p}</td></tr>`;
  }).join('');
}

function renderMarket() {
  const el = $('market');
  const syms = Object.keys(market).sort();
  if (!syms.length) { el.innerHTML = '<span class="sub">no ticks yet</span>'; return; }
  el.innerHTML = syms.map((s) =>
    `<div class="tile"><div class="sym">${s}</div><div class="px">${market[s]}</div></div>`).join('');
}

function prepend(listId, cls, text) {
  const li = document.createElement('li');
  li.className = cls;
  li.textContent = text;
  const ul = $(listId);
  ul.insertBefore(li, ul.firstChild);
  while (ul.children.length > 30) ul.removeChild(ul.lastChild);
}

// ── "How it's built" — the real source files, in a tabbed viewer ──
const FILES = [
  {
    name: 'orderdesk.js',
    lang: 'js',
    code:
`// the JS driver for this page — all through the generic bridge
import { createProcessor } from '@telamin/fluxtion-wasm-runtime';

const proc = await createProcessor('./classes.wasm');
const desk = proc.jsonBridge('OrderDeskHost');     // generic bridge over the order-desk SEP

// the position book is BROWSER state the SEP reads + stores via a JS-backed service
const positions = {};
desk.host.wirePositions(
  (sym) => positions[sym] || 0,                              // read  browser state
  (sym, pos) => { positions[sym] = pos; renderPositions(); } // store browser state
);

// each named sink drives a UI element (push egress)
desk.subscribe('marketData', (t) => showTick(t));            // { symbol, price }
desk.subscribe('accepted',   (o) => addRow('acc', o));       // { symbol, qty, position }
desk.subscribe('rejected',   (o) => addRow('rej', o));       // { symbol, qty, reason }

// send events as plain objects — the \`type\` routes them to the graph's decoders
function send(event) {
  desk.onEvent(event);
  showAudit(desk.audit());      // full node-by-node trail for the audit panel
}
send({ type: 'order', symbol: 'EURUSD', side: 'buy', qty: 100 });
send({ type: 'price', symbol: 'EURUSD', price: 1.0850 });`,
  },
  {
    name: 'OrderDeskHost.java',
    lang: 'java',
    code:
`// the WASM export shell = the generic JsonBridgeHost + ONE typed service wiring.
// Only wirePositions() is app-specific; everything else is the reusable bridge.
public class OrderDeskHost {

    private final CapabilitiesProcessor sep;
    private final JsonBridgeHost bridge;

    @JSExport
    public OrderDeskHost() {
        this.sep = new CapabilitiesProcessor();
        this.sep.init();
        this.bridge = new JsonBridgeHost(sep);
    }

    /** Adapt two JS functions into the PositionBook service the graph calls. */
    @JSExport
    public void wirePositions(PositionReader reader, PositionWriter writer) {
        PositionBook book = new PositionBook() {
            public int position(String symbol)       { return reader.position(symbol); }
            public void store(String symbol, int pos) { writer.store(symbol, pos); }
        };
        sep.registerService(new Service<>(book, PositionBook.class));
    }

    // ── generic bridge surface, delegated (app-agnostic) ──
    @JSExport public void onEventJson(String json)             { bridge.onEventJson(json); }
    @JSExport public void subscribe(String id, SinkListener l) { bridge.subscribe(id, l); }
    @JSExport public void addSink(String id)                   { bridge.addSink(id); }
    @JSExport public String lastSink(String id)                { return bridge.lastSink(id); }
    @JSExport public void signal(String name, String value)    { bridge.signal(name, value); }
    @JSExport public String drainAudit()                       { return bridge.drainAudit(); }
    @JSExport public void setLogLevel(String level)            { bridge.setLogLevel(level); }
}`,
  },
  {
    name: 'OrderRiskNode.java',
    lang: 'java',
    code:
`// the risk gate: reads/stores position via the injected service, applies a limit
// rule, and re-injects a TYPED Accepted/Rejected (the graph stays standard-shaped).
public class OrderRiskNode {

    private static final int LIMIT = 1000;   // abs position limit

    @Inject @NoTriggerReference
    public EventDispatcher eventDispatcher;

    private PositionBook book;

    @ServiceRegistered
    public void wire(PositionBook book, String name) {
        this.book = book;
    }

    @OnEventHandler(filterString = "order")
    public boolean onOrder(StringEvent e) {
        String json   = e.payload();
        String symbol = MiniJson.string(json, "symbol");
        String side   = MiniJson.string(json, "side");
        int qty       = MiniJson.intField(json, "qty", 0);

        if (book == null) {
            eventDispatcher.processReentrantEvent(new Rejected(symbol, qty, "no position service wired"));
            return false;
        }
        int signed = "sell".equals(side) ? -qty : qty;
        int next   = book.position(symbol) + signed;          // READ browser state

        if (qty <= 0) {
            eventDispatcher.processReentrantEvent(new Rejected(symbol, qty, "qty must be > 0"));
        } else if (Math.abs(next) > LIMIT) {
            eventDispatcher.processReentrantEvent(new Rejected(symbol, qty, "position limit breached: " + next));
        } else {
            book.store(symbol, next);                          // STORE browser state
            eventDispatcher.processReentrantEvent(new Accepted(symbol, signed, next));
        }
        return false;
    }
}`,
  },
  {
    name: 'PriceNode.java',
    lang: 'java',
    code:
`// decodes a type="price" event and re-injects a typed PriceTick
public class PriceNode {

    @Inject @NoTriggerReference
    public EventDispatcher eventDispatcher;

    @OnEventHandler(filterString = "price")
    public boolean onPrice(StringEvent e) {
        String json = e.payload();
        String px   = MiniJson.string(json, "price");
        double price = px == null || px.isEmpty() ? 0d : Double.parseDouble(px);
        eventDispatcher.processReentrantEvent(new PriceTick(MiniJson.string(json, "symbol"), price));
        return false;
    }
}`,
  },
  {
    name: 'model.java',
    lang: 'java',
    code:
`// the typed events + the service interface + the JSON mappers

public record Accepted(String symbol, int qty, int position) {}
public record Rejected(String symbol, int qty, String reason) {}
public record PriceTick(String symbol, double price) {}

// the service the graph calls — plain Java, implemented in JS at runtime
public interface PositionBook {
    int position(String symbol);
    void store(String symbol, int position);
}

// typed event → JSON for a named sink (plain statics = closed-world safe)
public final class AppFuncs {
    public static String acceptedJson(Accepted a) {
        return "{\\"symbol\\":\\"" + a.symbol() + "\\",\\"qty\\":" + a.qty() + ",\\"position\\":" + a.position() + "}";
    }
    public static String rejectedJson(Rejected r) {
        return "{\\"symbol\\":\\"" + r.symbol() + "\\",\\"qty\\":" + r.qty() + ",\\"reason\\":\\"" + r.reason() + "\\"}";
    }
    public static String priceJson(PriceTick p) {
        return "{\\"symbol\\":\\"" + p.symbol() + "\\",\\"price\\":" + p.price() + "}";
    }
}`,
  },
  {
    name: 'graph (GenerateCapabilities)',
    lang: 'java',
    code:
`// the order-desk graph, wired at build time (closed-world; compiled to the SEP)
cfg.addNode(new OrderRiskNode(), "orderRisk");
cfg.addNode(new PriceNode(),     "priceNode");

// typed results → JSON on named sinks the bridge reads
DataFlowBuilder.subscribe(Accepted.class) .map(AppFuncs::acceptedJson).sink("accepted");
DataFlowBuilder.subscribe(Rejected.class) .map(AppFuncs::rejectedJson).sink("rejected");
DataFlowBuilder.subscribe(PriceTick.class).map(AppFuncs::priceJson)   .sink("marketData");

// @ServiceRegistered wiring is reflective → register OrderRiskNode.wire reflectable
// for WASM via a TeaVM ReflectionSupplier (the WASM twin of GraalVM reflect-config):
//   new MethodDescriptor("wire", PositionBook.class, String.class, void.class)`,
  },
  {
    name: 'JsonBridgeHost.java',
    lang: 'java',
    code:
`// the REUSABLE generic bridge — SEP-agnostic (holds a DataFlow). OrderDeskHost,
// JsonHost and any SEP share it unchanged; only the event/sink names differ.
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

    // one consumer per sink, capturing the JS functor DIRECTLY in its closure (a
    // @JSFunctor stored in a collection loses callability in TeaVM), so pull + push coexist
    public void subscribe(String sinkId, SinkListener listener) {
        sep.addSink(sinkId, (Object v) -> { sinkValues.put(sinkId, v); listener.onValue(String.valueOf(v)); });
        wired.add(sinkId);
    }
    public void addSink(String sinkId) {
        if (wired.add(sinkId)) sep.addSink(sinkId, (Object v) -> sinkValues.put(sinkId, v));
    }
    public String lastSink(String sinkId) {
        addSink(sinkId);
        Object v = sinkValues.get(sinkId);
        return v == null ? null : String.valueOf(v);
    }
    public String drainAudit() { String s = String.join("\\n", audit); audit.clear(); return s; }
}`,
  },
];

function renderCode() {
  const hostEl = $('code');
  if (!hostEl) return;

  const det = document.createElement('details');     // collapsed by default
  det.className = 'code';
  const sum = document.createElement('summary');
  sum.textContent = `Source files (${FILES.length})`;
  det.appendChild(sum);

  const tabs = document.createElement('div');
  tabs.className = 'tabs';
  const wrap = document.createElement('div');
  wrap.className = 'code-wrap';
  const copy = document.createElement('button');
  copy.className = 'copy-btn';
  copy.textContent = 'copy';
  const pre = document.createElement('pre');
  let current = 0;

  function show(i) {
    current = i;
    [...tabs.children].forEach((t, j) => t.classList.toggle('active', j === i));
    pre.innerHTML = highlight(FILES[i].code, FILES[i].lang);
  }

  FILES.forEach((f, i) => {
    const b = document.createElement('button');
    b.className = 'tab';
    b.textContent = f.name;
    b.onclick = (e) => { e.preventDefault(); show(i); };
    tabs.appendChild(b);
  });

  copy.onclick = (e) => {
    e.stopPropagation();
    e.preventDefault();
    navigator.clipboard.writeText(FILES[current].code).then(() => {
      copy.textContent = 'copied';
      copy.classList.add('done');
      setTimeout(() => { copy.textContent = 'copy'; copy.classList.remove('done'); }, 1200);
    });
  };

  wrap.appendChild(copy);
  wrap.appendChild(pre);
  det.appendChild(tabs);
  det.appendChild(wrap);
  hostEl.appendChild(det);
  show(0);
}
renderCode();

// render the FULL audit record (event header and all), newest block on top, lightly
// colourised — the event header is kept, not stripped, so it reads as a real log.
function escHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
function fmtTime(ms) {
  const d = new Date(Number(ms));
  const p = (n, l = 2) => String(n).padStart(l, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`;
}
function showAudit(raw) {
  if (!raw || !raw.trim()) return;
  const lines = raw.split('\n');
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const e = escHtml(line);
    if (/^\s*eventLogRecord:/.test(line)) {
      // separator + a formatted stamp from this record's eventTime
      let t = '';
      for (let j = i + 1; j < Math.min(lines.length, i + 6); j++) {
        const m = lines[j].match(/eventTime:\s*(\d+)/);
        if (m) { t = fmtTime(m[1]); break; }
      }
      out.push(`<span class="au-sep">--- # ${t}</span>`);
      out.push(`<span class="au-ev">${e}</span>`);
    } else if (/^\s*(event|eventToString):/.test(line)) {
      out.push(`<span class="au-ev">${e}</span>`);
    } else if (/^\s*-\s*[\w$]+:/.test(line)) {
      out.push(`<span class="au-node">${e}</span>`);
    } else {
      out.push(e);
    }
  }
  const html = out.join('\n');
  const pre = $('audit');
  pre.innerHTML = pre.dataset.seeded ? pre.innerHTML + '\n' + html : html;   // APPEND to end
  pre.dataset.seeded = '1';
  // keep the panel bounded — drop the OLDEST lines (newest stay at the bottom)
  const all = pre.innerHTML.split('\n');
  if (all.length > 400) pre.innerHTML = all.slice(all.length - 400).join('\n');
  pre.scrollTop = pre.scrollHeight;   // auto-scroll to the newest
}
