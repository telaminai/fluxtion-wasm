// Copy the package's single-file runtime into each browser demo's web/ directory
// as `fluxtion-wasm-runtime.js`. The demos are served as static doc roots, so they
// cannot resolve the bare `@telamin/fluxtion-wasm-runtime` specifier — they vendor
// a verbatim copy instead. This keeps the copies byte-identical to the published
// source; run it after editing src/index.mjs.
//
//   node scripts/sync-vendored.mjs
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const src = fileURLToPath(new URL('src/index.mjs', root));

// web/ directories (relative to the repo root, one level above this package) that
// host a browser demo and vendor the runtime.
const targets = [
  '../examples/capabilities/web/fluxtion-wasm-runtime.js',
];

const header =
  '// VENDORED COPY — do not edit here.\n' +
  '// Source of truth: fluxtion-wasm-runtime/src/index.mjs (@telamin/fluxtion-wasm-runtime).\n' +
  '// Re-sync with: (cd fluxtion-wasm-runtime && npm run sync)\n\n';

const body = readFileSync(src, 'utf8');
let count = 0;
for (const rel of targets) {
  const dest = fileURLToPath(new URL(rel, root));
  // only write demos that already exist; never create a stray file
  const dir = dest.slice(0, dest.lastIndexOf('/'));
  if (!existsSync(dir)) {
    console.log(`skip  ${rel} (no such directory)`);
    continue;
  }
  writeFileSync(dest, header + body);
  console.log(`wrote ${rel}`);
  count++;
}
console.log(`synced ${count} vendored copy(ies) from src/index.mjs`);
