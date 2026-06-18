# Spec — Productizing fluxtion-wasm: helper libs, a standalone example, dev docs, a site page

**Status:** draft spec for review (2026-06-16). Companion to
[`fluxtion-wasm-runtime-spec.md`](fluxtion-wasm-runtime-spec.md) (the JS host lib,
already v0.1.0) and the `fluxtion-wasm-capabilities` demo (built). This spec covers
the four things that turn the *proven* WASM work into something a developer can pick
up and ship.

---

## 1. Goal

The WASM path is proven (a generated Fluxtion SEP → TeaVM → WASM-GC runs in Node and
the browser, JVM-identical; the generic JSON bridge drives any SEP; a full Order Desk
app works). But it all lives **inside the `fluxtion-wasm-testharness` monorepo** — the
proving ground — with the Java bridge **inlined** in the capabilities demo and the JS
runtime **vendored** as a file copy. Nothing is yet a thing a developer installs.

This spec shapes it into a product:

1. **Two published helper libs** — a Java one (`fluxtion-wasm-bootstrap`) and the
   existing JS one (`@telamin/fluxtion-wasm-runtime`).
2. **A standalone reference example** — `fluxtion-wasm-capabilities` extracted to
   depend on those libs as *real* dependencies (no inline copies).
3. **Developer getting-started docs** — the honest "zero → a WASM Fluxtion app" path.
4. **A `fluxtion-wasm` page on the site** (fluxtion-web) — what it is, the live demo,
   the start path, the honest limits.

**Standing constraints** (carry into every artifact): WASM stays **"in development"**
in marketing; **no performance numbers** (native/WASM is not benchmarked — `bench()`
produces honest on-device figures, we don't quote any); **generation needs a
generator** (cloud key in `~/.fluxtion` or local `fluxtion-generator-core`) — never
imply offline generation; the reflective `@ServiceRegistered` path needs a TeaVM
`ReflectionSupplier` (don't present it as free).

---

## 2. Current state — what exists, what's inline

| Piece | Where it is now | Productized? |
|---|---|---|
| JS host lib `@telamin/fluxtion-wasm-runtime` | `fluxtion-wasm-runtime/` (npm package, v0.1.0) | ✅ built, **not extracted** to its own home; vendored into demos via `sync` |
| Java bridge (`JsonBridgeHost`, `MiniJson`, `SinkListener`) | **inline** in `fluxtion-wasm-capabilities/src/.../bridge/` | ❌ never extracted |
| The capability grid + Live Order Desk demo | `fluxtion-wasm-capabilities/` (one Maven module + `web/`) | ⚠️ a demo, bundles its own bridge + vendored runtime |
| Conformance harness (dual Java/Node SEP runners), spikes | `fluxtion-wasm-conformance/`, `spike/` | proving ground — **stays** in the testharness |

The split between **reusable** and **per-app** is already clean in the source (this is
what makes extraction low-risk):

- **Reusable Java** → `bridge/JsonBridgeHost` (the SEP-agnostic bridge over `DataFlow`),
  `bridge/MiniJson` (reflection-free flat-JSON reader), `bridge/SinkListener`
  (`@JSFunctor` so a sink value becomes a JS callback).
- **Per-app Java** → the concrete `@JSExport` shell (`CapHost`, `OrderDeskHost`), the
  app's event types + decoder nodes, app `@JSFunctor` service interfaces
  (`JsPriceStore`, `PositionReader/Writer`). The generic `JsonHost` shell is the
  *template* for the per-app shell (later generator-emitted — §7 P5).
- **Reusable JS** → `createProcessor` / `FluxtionProcessor.newHost` / `.jsonBridge` →
  `JsonBridge` facade (`onEvent`/`send`/`track`/`query`/`subscribe`/`signal`/`audit`/
  `get`) / `bench`.

---

## 3. The artifacts (the productized shape)

### 3a. `fluxtion-wasm-bootstrap` (Java, Maven) — NEW

The reusable Java host layer, compiled *into* each SEP wasm. Extract verbatim from
the capabilities `bridge/` package:

- `JsonBridgeHost(DataFlow)` — ingress (`onEventJson` routes by `type` to a graph
  decoder; `onEvent(type,payload)`), egress (`addSink`/`lastSink` pull, `subscribe`
  push via `SinkListener`, `getStreamed`), `signal`, `registerService`, audit
  (`drainAudit`/`setLogLevel`). Holds a `DataFlow`, so it is reused unchanged for any
  SEP.
- `MiniJson` — `string(json,key)` / `intField(...)`, key-aware (matches `"k":`, not a
  value substring — the bug fixed 2026-06-16), TeaVM-clean.
- `SinkListener` (`@JSFunctor`), and a small `@JSFunctor` toolkit doc for JS-backed
  services.
- A **documented `JsonHost` template** — the fixed `@JSExport` shell (the only
  per-SEP line is the processor class). Shipped as a copy-paste template + javadoc,
  because `@JSExport` must sit on the concrete class; generator-emission is P5.

**Constraints:** must be TeaVM-clean (no reflection, no `SerializedLambda`, no
`java.util.logging`, no threads/IO). Depends only on `fluxtion-runtime` +
`teavm-jso` (provided). Class-file 17/21 fine (TeaVM consumes it at build).

**Naming/licensing (open, §8):** `com.telamin.fluxtion:fluxtion-wasm-bootstrap`.
License to match the runtime tier (Apache-2.0) since it's a thin client helper, not
the metered generator — confirm against `docs/licensing/`.

### 3b. `@telamin/fluxtion-wasm-runtime` (JS, npm) — EXISTS, relocate

Already v0.1.0 (single ESM, zero deps, types, smoke test). Action: lift it out of the
testharness into the product home (§4); demos consume it as a real dependency (or the
vendored `sync` for static-served pages, which stays as the browser-without-bundler
escape hatch).

### 3c. `fluxtion-wasm-capabilities` (the reference example) — EXTRACT

The canonical "this is how you build a WASM Fluxtion app." Re-home it to depend on
`fluxtion-wasm-bootstrap` (drop the inline `bridge/`) and `@telamin/fluxtion-wasm-runtime`
(drop the vendored copy, or keep `sync` for the static page). It keeps both faces:

- the **capability grid** (`web/`) — self-proving "what survives WASM," and
- the **Live Order Desk** (`web/app-orderdesk.html`) — a real app: typed events →
  named sinks → DOM, a JS-backed service reading/storing browser state, a live audit
  log, the "How it's built" tabbed source.

This is the example the docs (§5) and the site page (§6) point at.

---

## 4. Repository / module structure

Three options; **recommendation: a dedicated `fluxtion-wasm` repo** so the product has
a clean published home separate from the proving ground.

```
fluxtion-wasm/                         (new, published home)
  bootstrap/        fluxtion-wasm-bootstrap   (Java, Maven → repsy/central)
  runtime/          @telamin/fluxtion-wasm-runtime (JS → npm)   [moved from testharness]
  examples/
    capabilities/   the reference grid + Order Desk (depends on bootstrap + runtime)
  docs/             getting-started + API references (§5)

fluxtion-wasm-testharness/             (stays — the proving ground)
  conformance/      dual Java/Node SEP runners (JUnit), now testing the PUBLISHED libs
  spike/            historical experiments
  docs/             this spec + the design docs
```

Alternatives: (b) promote `fluxtion-wasm-runtime` (already top-level) to be that home;
(c) keep everything in the testharness and just publish from there. (a) is cleanest for
a developer landing on the repo; (c) is least work. **Decide in §8.**

The testharness's value continues: the conformance harness becomes the regression gate
for the **published** libs (every operator/DSL shape: JVM == WASM).

---

## 5. Developer getting-started docs (the doc set)

The honest "zero → running WASM Fluxtion app" path. Lives in `fluxtion-wasm/docs/`,
surfaced on the site (§6).

1. **`getting-started.md`** — the spine:
   - **Prerequisites:** Java 21, TeaVM 0.14.1 (`targetType=WEBASSEMBLY_GC`), a
     **generator** (cloud key in `~/.fluxtion`, or local `fluxtion-generator-core`),
     Node 20+ / a WASM-GC browser.
   - **The pipeline (one diagram):** DSL/nodes → **generate SEP** (cloud or local) →
     add the `@JSExport` host (`JsonHost` template over `fluxtion-wasm-bootstrap`) →
     **TeaVM compile** (`+ copy-webassembly-gc-runtime`) → copy to `web/` → load with
     `@telamin/fluxtion-wasm-runtime` → drive via the JSON bridge.
   - **Hello-SEP walkthrough:** a ~30-line end-to-end (one decoder node, one sink;
     `sep.onEvent({...})` → `sep.query(...)`). Downloadable, runs unchanged.
2. **`bridge-api.md`** — the bridge reference: ingress (`onEvent`/`send`), egress
   (`track`+`query` pull, `subscribe` push), `signal`, `audit`/`setLogLevel`, `get`;
   the **decoder-node pattern** (re-inject a typed event so the graph stays typed); the
   **service patterns** (`@ExportService` typed handle, `@ServiceRegistered` +
   `ReflectionSupplier`, JS-backed `@JSFunctor`, callback-via-event).
3. **`build-config.md`** — the pom reference: deps (`fluxtion-runtime` + `-builder` +
   `fluxtion-wasm-bootstrap` + `teavm-classlib`/`-jso` provided), the TeaVM plugin
   (compile + `copy-webassembly-gc-runtime`), the resources copy to `web/`, the local
   `regen`/`fix-validation` profile pattern.
4. **`deploy-targets.md`** — Node / browser / web worker / edge (CF/Fastly) — promote
   the existing [`deployment-targets.md`](deployment-targets.md).
5. **`limits.md`** — the honest boundary: reflective `@ServiceRegistered` needs a
   `ReflectionSupplier` (the generator emits the GraalVM twin already); single-threaded
   (no ForkJoin); WASM-GC browser/Node-20+ only; **generation needs a generator**; **not
   benchmarked** — use `bench()` for on-device numbers, quote none.

The capability grid is itself part of the docs — it's the *runnable* reference for
"what works," produced live, not asserted.

---

## 6. The `fluxtion-wasm` site page (fluxtion-web)

A new route on the SvelteKit site (e.g. `/fluxtion-wasm`, plus a nav entry).

**Content:**
- **What it is:** run a Fluxtion event processor in the browser / at the edge —
  deterministic, single-threaded, no server and no AI on the path; same logic as the
  JVM.
- **Status banner:** honest **"in development"** (per the marketing constraint).
- **Live demo (embed or link):** the capability grid + the Live Order Desk from
  `fluxtion-wasm-capabilities/web/` — served static (the jars/wasm are build artifacts,
  same `fetch-libs`/gitignore pattern as the playground if needed).
- **Get started:** the §5 path condensed, with the two helper libs (npm + Maven badges)
  and a copy-paste hello-SEP.
- **Honest limits:** the §5 `limits.md` summary — no perf numbers, generator required,
  the reflective seam.

**Placement / reuse:** the capabilities `web/` app is the demo asset; host it under
`static/` (or an iframe) rather than re-implementing. Follow the site's link-verify +
icon conventions ([[feedback_marketing_verify_links_and_icons]]). The page links to the
docs (§5) and the GitHub repo (§4).

**Out of scope (call it out on the page):** the *hosted* in-browser TeaVM compile (the
playground "TeaVM project type", `in-browser-compile-spec.md`) is a later, metered
server stage — the page describes the developer pipeline, not a one-click cloud build.

---

## 7. Phasing

| Phase | Deliverable | Status (2026-06-18) |
|---|---|---|
| **P0** | JS runtime productized (v0.1.0); capabilities grid + Order Desk built; bridge proven | ✅ done |
| **P1** | Extract **`fluxtion-wasm-bootstrap`** (Java lib, Apache-2.0); capabilities depends on it (inline `bridge/` deleted) | ✅ done + verified — fluxtion 1.0.8 + bootstrap 0.1.0, 14/14 WASM probes pass |
| **P2** | Re-home bootstrap + runtime + capabilities into the **`telaminai/fluxtion-wasm`** repo; testharness becomes the proving ground | ✅ **done** — repo created (`bootstrap/`, `runtime/`, `examples/capabilities/`, `docs/`, aggregator pom); `mvn install` green, 14/14 probes, runtime smoke 6/6; testharness duplicates removed + README repointed |
| **P3** | Getting-started guide (§5) + hello-SEP | ✅ done — [`getting-started.md`](getting-started.md) |
| **P4** | `/fluxtion-wasm` site page (§6) — embedded demo, start path, "in development" | ✅ **done + on main** (fluxtion-web): page live, dev-nav entry, "Why not just…?" + Java-team framing, embedded Order Desk, on-device `bench()`, `/wasm-introduction` grounding page |
| **PUB** | **Publish the libs** — `fluxtion-wasm-bootstrap` → Repsy Maven, `@telamin/fluxtion-wasm-runtime` → Repsy npm (§9) | ✅ **config landed** — both pipelines wired, `REPSY_USER`/`REPSY_PASSWORD` set; push to `release` / `release-npm` to cut |
| **P5a** | Generator-emitted `JsonHost` shell + `ReflectionSupplier`, returned as the **metered** bundle in the compiler response | ✅ **implemented** ([`p5-generator-emitted-host.md`](p5-generator-emitted-host.md)) on `feature/p5a-generate-wasm-host` (both repos): `generateWasmHost` flag → server renders host + main + `ReflectionSupplier`/SPI → gated at the HTTP boundary → client writes. Unit + e2e tested; not yet pushed/merged |
| **P5b** | ~~Hosted TeaVM compile~~ — **retracted** | ❌ not a thing: TeaVM→WASM is a **client-side** compile the user runs locally. The metered piece is the P5a host bundle, not a server compile stage |

P1–P4 are independent of the still-spec Tier-1/Tier-2 boundary work in the runtime
spec; they package what already works.

---

## 8. Decisions (2026-06-16) — resolved

1. **Repo home** → **promote `fluxtion-wasm-runtime`** to be the product home: it
   gains `bootstrap/` (Java) + `examples/capabilities/` alongside the existing JS
   package. Not a new repo, not left in the testharness.
2. **`fluxtion-wasm-bootstrap` license** → **Apache-2.0** (a thin client helper, not
   the metered generator).
3. **Generator-emitted host (P5)** → **keep the `JsonBridgeHost` public API stable**;
   it is the contract the generator will emit the `@JSExport` host against. Treat it as
   a published API surface from P1 (semver, no breaking changes without a major bump).
4. **Site page** → **one `/fluxtion-wasm` route**, demo **embedded via an `<iframe>`**
   of the static capabilities app (not a re-implementation, not a link-out).
5. **`fluxtion-builder-all-java8`** → out of scope here; the in-browser CheerpJ preview
   (DSL type-check) is a different path from this TeaVM runtime pipeline — keep them
   distinct on the page.

> P1 starts now: extract `fluxtion-wasm-bootstrap` (Apache-2.0, package
> `com.telamin.fluxtion.wasm.bootstrap`) and make `fluxtion-wasm-capabilities` depend
> on it. The module lands top-level in the testharness first, then moves into the
> promoted `fluxtion-wasm-runtime` home (P2).

---

## 9. Publishing the libs (PUB) — both to Repsy

Two artifacts, **both to Repsy** (Maven + npm sides of the same account), so a single
credential pair publishes everything. Both are public, Apache-2.0 client helpers — **not**
the metered generator — so they publish freely with no licence gate.

| Artifact | Registry | Coordinates | Consumed by |
|---|---|---|---|
| `fluxtion-wasm-bootstrap` (Java) | **Repsy Maven** (`fluxtion-public`) | `com.telamin.fluxtion:fluxtion-wasm-bootstrap` | the capabilities app, the generator-emitted host (P5a), any user WASM project |
| `@telamin/fluxtion-wasm-runtime` (JS) | **Repsy npm** (`fluxtion/npm`) | `@telamin/fluxtion-wasm-runtime` | the browser/Node loader on the `/fluxtion-wasm` page and in user apps |

**Implemented** (this is what's in the repo, not a plan). A single secret pair
`REPSY_USER` / `REPSY_PASSWORD` drives both publishes.

### 9.1 Java → Repsy Maven

A deliberate deviation from `mongoose-plugins`: bootstrap is a single standalone lib, so
it uses a **plain `mvn deploy`** rather than the `release:prepare`/`release:perform`
version-dance (which needs SNAPSHOT versioning and pushes version-bump commits back). Bump
`bootstrap/pom.xml` `<version>` when you want a new release — Repsy's release repo rejects
a re-deploy of an existing version.

- **`bootstrap/pom.xml`** — `<scm>`, a `<repositories>` entry for the public read mirror
  (`https://repo.repsy.io/mvn/fluxtion/fluxtion-public`, so a clean CI/external build needs
  no pre-seeded `~/.m2`), and `<distributionManagement>` with the **write** URL
  (`https://repo.repsy.io/fluxtion/fluxtion-public`, no `/mvn/`) under id `repsy-fluxtion`.
- **`.github/settings.xml`** — server `repsy-fluxtion` mapping `${env.MAVEN_USERNAME}` /
  `${env.MAVEN_CENTRAL_TOKEN}` (no secrets in-repo).
- **`.github/workflows/release.yml`** — `on: push: branches: [release]`; JDK 21; runs
  `mvn -B -s .github/settings.xml -pl bootstrap -DskipTests deploy` (the `-pl bootstrap`
  scopes the reactor so the TeaVM example + the host-emitter are not published), then
  cuts a **git tag + GitHub Release** `bootstrap-v<version>` via `gh release create`
  (auto-creating the tag at the deployed commit, with notes carrying the Maven
  coordinates), then merges `release` → `main`. Env maps
  `MAVEN_USERNAME=${{ secrets.REPSY_USER }}`, `MAVEN_CENTRAL_TOKEN=${{ secrets.REPSY_PASSWORD }}`,
  and `GH_TOKEN=${{ github.token }}` for the release. (Plain `deploy` does not tag — the
  `gh release` step is what makes a release recoverable via `git checkout bootstrap-v<version>`.)
- **`.github/workflows/main.yml`** — CI on push/PR to `main`: builds + tests `bootstrap`
  and `host-emitter` (`-pl bootstrap,host-emitter verify`).

Consumers add the read repository above plus the dependency.

### 9.2 JS → Repsy npm

`runtime/package.json` `publishConfig.registry` points at
`https://repo.repsy.io/fluxtion/npm/` (scope `@telamin`).

- **`.github/workflows/publish-npm.yml`** — `on: push: branches: [release-npm]`; writes a
  throw-away `runtime/.npmrc` (scope→registry + `_auth` = base64 of `REPSY_USER:REPSY_PASSWORD`
  + `always-auth`), runs `npm test` then `npm publish`, cuts a git tag + GitHub Release
  `runtime-v<version>` (`gh release create`, notes carrying the npm install line), then
  deletes the `.npmrc`. `.npmrc` is gitignored so a stray local one can't leak.

Consumers point the `@telamin` scope at the registry:
`npm config set @telamin:registry https://repo.repsy.io/fluxtion/npm/`.

### 9.3 How to cut a release

The workflows run from the branch you push to, so the **release branch must carry the
latest workflow** (which lives on `main`). The procedure for each side:

1. `git checkout release && git merge main` (bring the latest workflow + code across).
2. Bump the version — `bootstrap/pom.xml` `<version>` (Java) — in that same branch.
3. `git push origin release`. CI deploys to Repsy, cuts the `bootstrap-v<version>` tag +
   GitHub Release, and merges `release` → `main`.

For JS, the same with `release-npm` and `runtime/package.json` `version` → `runtime-v<version>`.

Re-pushing an already-published version still re-deploys to Repsy (it permits
overwriting a version), but then **fails at the `gh release create` step** because the
`bootstrap-v<version>` / `runtime-v<version>` Release already exists — so a forgotten
version bump surfaces as a red ❌ on the release, not a silent re-release. Bump the
version. Tags + notes are published under
**https://github.com/telaminai/fluxtion-wasm/releases**.

### 9.4 Status

✅ **Live.** `fluxtion-wasm-bootstrap:0.1.0` (Repsy Maven) and
`@telamin/fluxtion-wasm-runtime@0.1.0` (Repsy npm) are published, each with a GitHub
Release. Both pipelines are wired with tag + Release creation; the `REPSY_USER` /
`REPSY_PASSWORD` secrets are set. The local reactor still builds everything and the
capabilities app consumes `bootstrap` as a reactor dependency.
