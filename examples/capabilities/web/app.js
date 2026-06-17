// Interactive capability learning resource. Loads the WASM event processor, renders
// a "Getting started" block, a summary grid, and a card per capability (what it
// validates, the Java that receives the call, the JS that makes it, a Run button +
// live output, and a "Show full example" expand with copy-pasteable Java + JS).
import { createProcessor } from './fluxtion-wasm-runtime.js';
import { CAPABILITIES, GETTING_STARTED } from './capabilities.js';
import { highlight } from './highlight.js';

// infer the highlighter language from a code block's title
function langFromTitle(title) {
  if (/javascript/i.test(title)) return 'js';
  if (/build/i.test(title)) return 'bash';
  return 'java';
}

const status = document.getElementById('status');
const summaryBody = document.getElementById('summary');
const cards = document.getElementById('cards');
const gettingStarted = document.getElementById('gettingStarted');

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

function badge(supported) {
  return el('span', 'badge ' + (supported ? 'yes' : 'no'), supported ? '✓ supported' : '✗ not supported');
}

// a titled code block with a copy button
function codeBlock(title, code) {
  const wrap = el('div', 'code-wrap');
  wrap.appendChild(el('div', 'code-title', title));
  const btn = el('button', 'copy-btn', 'copy');
  btn.onclick = () => {
    navigator.clipboard.writeText(code).then(() => {
      btn.textContent = 'copied';
      btn.classList.add('done');
      setTimeout(() => { btn.textContent = 'copy'; btn.classList.remove('done'); }, 1200);
    });
  };
  wrap.appendChild(btn);
  const pre = el('pre');
  pre.innerHTML = highlight(code, langFromTitle(title));   // colourised; copy uses the raw `code`
  wrap.appendChild(pre);
  return wrap;
}

function renderGettingStarted() {
  const d = el('details');
  d.appendChild(el('summary', null, 'Getting started — build it, load it, call it'));
  const body = el('div', 'body');
  body.appendChild(el('p', 'what', 'Shared setup used by every example below. Build the wasm, ' +
    'load it in the browser, and construct the @JSExport host once — then each capability is one call.'));
  body.appendChild(codeBlock('Build (Java 21)', GETTING_STARTED.build));
  body.appendChild(codeBlock('JavaScript — load + host (once)', GETTING_STARTED.js));
  body.appendChild(codeBlock('Java — the @JSExport host skeleton', GETTING_STARTED.java));
  d.appendChild(body);
  gettingStarted.appendChild(d);
}

// silently determine support for the summary badge
function probe(cap, host, bridge, desk) {
  if (cap.note) return false;
  try { cap.run(host, bridge, desk); return true; } catch (_) { return false; }
}

function previewCodes(cap) {
  const code = el('div', 'codes');
  code.appendChild(codeBlock('Java — receives the call', cap.java));
  code.appendChild(codeBlock('JavaScript — makes the call', cap.js));
  return code;
}

function fullExample(cap) {
  const d = el('details', 'full');
  d.appendChild(el('summary', null, 'Show full example (Java + JS)'));
  const body = el('div', 'body');
  body.appendChild(codeBlock('Java — full', cap.javaFull || cap.java));
  body.appendChild(codeBlock('JavaScript — full', cap.jsFull || cap.js));
  d.appendChild(body);
  return d;
}

function renderCard(cap, host, bridge, desk) {
  const card = el('section', 'card');
  card.id = 'cap-' + cap.key;

  const head = el('div', 'card-head');
  head.appendChild(el('h2', null, cap.label));
  const supported = cap.note ? false : probe(cap, host, bridge, desk);
  head.appendChild(badge(supported));
  card.appendChild(head);

  card.appendChild(el('p', 'what', cap.what));
  if (cap.href) {
    const p = el('p', 'card-link');
    const a = el('a', null, cap.linkText || 'Open →');
    a.href = cap.href;
    p.appendChild(a);
    card.appendChild(p);
  }
  if (cap.note) { cards.appendChild(card); return; }

  card.appendChild(previewCodes(cap));

  const runRow = el('div', 'run-row');
  const btn = el('button', 'run-btn', supported ? 'Run' : 'Run (expect failure)');
  const out = el('pre', 'output');
  out.textContent = '(not run yet)';
  btn.onclick = () => {
    out.classList.remove('err', 'ok');
    const ts = new Date().toLocaleTimeString();
    try {
      const result = cap.run(host, bridge, desk);
      out.classList.add('ok');
      out.textContent = `> ${cap.js}\n${result}\n\n[ran ${ts}]`;
    } catch (e) {
      out.classList.add('err');
      out.textContent = `> ${cap.js}\n✗ ${(e && e.message) || e}\n\n[ran ${ts}]`;
    }
  };
  runRow.appendChild(btn);
  card.appendChild(runRow);
  card.appendChild(out);
  card.appendChild(fullExample(cap));

  cards.appendChild(card);
  return supported;
}

function renderSummaryRow(cap, supported) {
  if (cap.group) {
    const tr = el('tr', 'group');
    const td = el('td');
    td.colSpan = 2;
    td.textContent = cap.group;
    tr.appendChild(td);
    summaryBody.appendChild(tr);
    return;
  }
  const tr = el('tr');
  const tdLabel = el('td');
  const a = el('a', null, cap.label);
  a.href = '#cap-' + cap.key;
  tdLabel.appendChild(a);
  const tdBadge = el('td');
  tdBadge.appendChild(badge(supported));
  tr.append(tdLabel, tdBadge);
  summaryBody.appendChild(tr);
}

try {
  if (!globalThis.TeaVM || !globalThis.TeaVM.wasmGC) {
    throw new Error('WASM-GC not available — try a recent Chrome, Firefox, or Safari.');
  }
  const proc = await createProcessor('./classes.wasm');
  const host = proc.newHost('CapHost');
  // the generic JSON bridge over the SAME wasm (a second exported host) — lets the
  // bridge card drive the SEP with plain objects, no bespoke verb.
  const bridge = proc.jsonBridge('JsonHost');
  // the Order Desk app host (third exported host); wire its JS-backed position store
  // so the example-app card can run a risk-checked order live.
  const desk = proc.jsonBridge('OrderDeskHost');
  const deskPositions = {};
  desk.host.wirePositions((s) => deskPositions[s] || 0, (s, p) => { deskPositions[s] = p; });
  status.textContent = 'Loaded — capabilities run live in WASM on this device. Click Run on any card.';

  renderGettingStarted();

  const support = new Map();
  for (const cap of CAPABILITIES) {
    if (cap.group) continue;
    support.set(cap.key, renderCard(cap, host, bridge, desk));
  }
  for (const cap of CAPABILITIES) {
    renderSummaryRow(cap, cap.note ? false : support.get(cap.key));
  }
} catch (e) {
  status.textContent = 'Failed to load WASM: ' + ((e && e.message) || e);
  status.classList.add('err');
}
