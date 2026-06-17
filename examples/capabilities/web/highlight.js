// Tiny self-contained syntax highlighter for the Java / JS / shell snippets on this
// page. No external dependency (the page is served as static files): a single-pass
// scanner that emits <span class="hl-*"> tokens, escaping HTML as it goes. It is not
// a full parser — just enough lexing (comments, strings, annotations, numbers,
// keywords, types, call names) to make the examples readable.

const KEYWORDS = new Set([
  // declarations / control — Java + JS, unioned (a keyword in either language)
  'abstract', 'assert', 'async', 'await', 'boolean', 'break', 'byte', 'case', 'catch',
  'char', 'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum',
  'export', 'extends', 'final', 'finally', 'float', 'for', 'from', 'function', 'goto',
  'if', 'implements', 'import', 'instanceof', 'int', 'interface', 'let', 'long',
  'native', 'new', 'of', 'package', 'private', 'protected', 'public', 'record',
  'return', 'short', 'static', 'strictfp', 'switch', 'synchronized', 'throw', 'throws',
  'transient', 'try', 'typeof', 'var', 'void', 'volatile', 'while', 'yield',
]);

const LITERALS = new Set(['true', 'false', 'null', 'undefined', 'this', 'super']);

const ID_START = /[A-Za-z_$]/;
const ID_PART = /[A-Za-z0-9_$]/;

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function span(cls, s) {
  return `<span class="hl-${cls}">${esc(s)}</span>`;
}

/**
 * Highlight source into an HTML string of token spans.
 * @param {string} code
 * @param {'java'|'js'|'bash'} [lang]
 * @returns {string} HTML (already escaped)
 */
export function highlight(code, lang = 'java') {
  let out = '';
  let i = 0;
  const n = code.length;

  while (i < n) {
    const c = code[i];
    const c2 = code[i + 1];

    // line comment // (java/js) or # (bash)
    if ((c === '/' && c2 === '/') || (c === '#' && lang === 'bash')) {
      let j = i + 1;
      while (j < n && code[j] !== '\n') j++;
      out += span('cm', code.slice(i, j));
      i = j;
      continue;
    }
    // block comment /* ... */
    if (c === '/' && c2 === '*') {
      let j = i + 2;
      while (j < n && !(code[j] === '*' && code[j + 1] === '/')) j++;
      j = Math.min(n, j + 2);
      out += span('cm', code.slice(i, j));
      i = j;
      continue;
    }
    // string: " ' or ` (template) — honours backslash escapes
    if (c === '"' || c === "'" || c === '`') {
      let j = i + 1;
      while (j < n && code[j] !== c) {
        if (code[j] === '\\') j++;
        j++;
      }
      j = Math.min(n, j + 1);
      out += span('st', code.slice(i, j));
      i = j;
      continue;
    }
    // annotation @Ident (Java)
    if (c === '@' && c2 && ID_START.test(c2)) {
      let j = i + 1;
      while (j < n && ID_PART.test(code[j])) j++;
      out += span('an', code.slice(i, j));
      i = j;
      continue;
    }
    // number
    if (c >= '0' && c <= '9') {
      let j = i + 1;
      while (j < n && /[0-9._a-fxXLderp+-]/.test(code[j])) j++;
      out += span('nu', code.slice(i, j));
      i = j;
      continue;
    }
    // identifier / keyword / type / call
    if (ID_START.test(c)) {
      let j = i + 1;
      while (j < n && ID_PART.test(code[j])) j++;
      const word = code.slice(i, j);
      let k = j;
      while (k < n && code[k] === ' ') k++;
      let cls = '';
      if (KEYWORDS.has(word)) cls = 'kw';
      else if (LITERALS.has(word)) cls = 'li';
      else if (code[k] === '(') cls = 'fn';        // a call / method name
      else if (/^[A-Z]/.test(word)) cls = 'ty';    // Capitalized → type/class
      out += cls ? span(cls, word) : esc(word);
      i = j;
      continue;
    }
    // anything else
    out += esc(c);
    i++;
  }
  return out;
}
