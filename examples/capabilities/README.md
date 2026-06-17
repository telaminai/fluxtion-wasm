# fluxtion-wasm-capabilities

**What works when you compile a Fluxtion event processor to WebAssembly?** This
project answers that with live proof: it drives every capability through a real
WASM build and presents it in the browser as an **interactive learning resource** —
a summary grid plus a card per capability showing *what it validates*, the *Java
that receives the call*, the *JavaScript that makes it*, and a **Run** button with
live output. Every ✓/✗ is produced by running the capability *live in WASM in your
browser*, never typed by hand — a ✗ Run shows the real error.

It doubles as a regression harness for runtime fixes (see *Testing a runtime fix*
below).

See `docs/SPEC.md` for the design, `docs/PHASING.md` for the full probed
capability table, and `CLAUDE.md` to hand the project to an AI agent.

## What it found (fluxtion-runtime 1.0.8, TeaVM 0.14.1)

| Capability | WASM |
|---|---|
| DSL map / filter → sink, `addSink` egress | ✅ |
| `getStreamed` (read a flow’s state by id) | ✅ |
| `publishSignal` / `subscribeToSignal` | ✅ |
| `triggerCalculation` | ✅ |
| Exported services (`@ExportService`) | ✅ |
| Audit logging + change log level (`addEventAudit`, de-JUL’d in 1.0.8) | ✅ |
| Service/callback delivered **as an event** | ✅ |
| Injected services (`@ServiceRegistered`) | ❌ reflective registry (`ServiceRegistryNode#scanNode`) |

(Full detail, root causes, and the upstream fixes are in `docs/PHASING.md`.)

> **Requires fluxtion ≥ 1.0.8** (the audit path uses the de-JUL’d runtime
> `EventLogManager`). To build against released 1.0.7, regenerate with the
> `-Pshim-audit` profile (JUL-free shim) — see *Testing a runtime fix*.

## Run it

Needs **Java 21** (TeaVM 0.14.1 requires it) and **node** on your PATH.

```bash
# build everything: JVM probe test, compile the SEP to WASM, copy it into web/
JAVA_HOME=/path/to/jdk-21 mvn clean install

# serve the grid (must be http, not file://) and open it
cd web && python3 -m http.server 8000
#   → http://localhost:8000
```

You'll see the summary grid (each linking to its card) and a card per capability
with the description, the Java + JavaScript, and a **Run** button that executes it
live in WASM and shows the output (audit logs, sink values, service results, or the
real error for the unsupported ones).

The browser UI is three files in `web/`: `capabilities.js` (the capability
definitions — description, Java/JS snippets, and the live `run(host)` per capability,
DOM-free so it's testable headless), `app.js` (renders the grid + cards), and
`index.html`. The `fluxtion-wasm-runtime.js` loader is the extractable, npm-ready lib.

### Run the probes from the command line (no browser)

```bash
node probe/run-probes.mjs        # drives each capability in Node, prints OK / FAIL
```

The JVM baseline is a JUnit test: `mvn test` prints `[JVM] OK/FAIL` per capability.

## How it works (one module)

```
src/main/java/com/telamin/fluxtion/wasm/cap/
   *Node, CapFuncs, Calculator, Greeter…   the nodes/funcs exercising each capability
   generated/CapabilitiesProcessor.java     the generated SEP  (committed)
   CapabilitiesHost                          plain-Java host: one probe method per capability
   gen/GenerateCapabilities                  regenerates the SEP (run by hand)
   host/CapHost, host/ExportMain             @JSExport surface over CapabilitiesHost
web/
   index.html, app.js                        the capability grid (self-reporting)
   fluxtion-wasm-runtime.js                  extractable JS lib (createProcessor) — npm-ready
   classes.wasm, *-runtime.js                copied in by the build (gitignored)
```

One module: the generated SEP is **committed**, so there's no generator↔SEP build
cycle; `fluxtion-builder` is on the classpath only for the manual generator, and
TeaVM compiles from `ExportMain` so it never reaches it.

To add a capability: add a node + a `CapabilitiesHost` probe method + a `@JSExport`
method on `CapHost` + a grid row in `web/app.js`. Regenerate the SEP if you changed
the graph (see below). `CLAUDE.md` has the step-by-step.

## Regenerating the SEP

The SEP is committed; regenerate it after changing the graph (needs a Fluxtion
generator — cloud key in `~/.fluxtion`, or a local generator on the classpath):

```bash
mvn compile exec:java -Dexec.mainClass=com.telamin.fluxtion.wasm.cap.gen.GenerateCapabilities
cp target/generated-sources/fluxtion/com/telamin/fluxtion/wasm/cap/generated/CapabilitiesProcessor.java \
   src/main/java/com/telamin/fluxtion/wasm/cap/generated/
```

(The generator references the SEP-less node classes; if `CapabilitiesHost` won't
compile because the SEP is missing, move the host/test aside, regenerate, restore.)

## Testing a runtime fix against the matrix (snapshots)

The grid is the oracle for "did my fix make this capability work in WASM?". The
`fix-validation` Maven profile pre-wires this: it adds `fluxtion-generator-core`
so generation runs **locally** (against *your* generator branch, not the deployed
cloud generator), and versions are overridable on the CLI.

```bash
# 1. install your snapshots to ~/.m2 (runtime + builder, and generator-core if you
#    changed the generator)
#    e.g. in the fluxtion / fluxtion-compiler repos:  mvn -DskipTests install

# 2. regenerate the SEP with your snapshots, generated LOCALLY. Add
#    -Dcap.runtimeAudit=true to test the REAL EventLogManager (not the JUL-free shim).
mvn -Pfix-validation -Dfluxtion.version=1.0.8-SNAPSHOT -Dgenerator.version=1.0.48-SNAPSHOT \
    [-Dcap.runtimeAudit=true] \
    compile exec:java -Dexec.mainClass=com.telamin.fluxtion.wasm.cap.gen.GenerateCapabilities
cp target/generated-sources/fluxtion/com/telamin/fluxtion/wasm/cap/generated/CapabilitiesProcessor.java \
   src/main/java/com/telamin/fluxtion/wasm/cap/generated/

# 3. build + run the matrix against the snapshots
mvn -Pfix-validation -Dfluxtion.version=1.0.8-SNAPSHOT clean install
node probe/run-probes.mjs
```

Two kinds of fix:

- **`ServiceRegistryNode` / M4 static wiring (generator fix).** The `injectedService`
  row flips ❌→✅ once the generated SEP carries static `wireServices` instead of the
  reflective registry. **Must regenerate with `-Pfix-validation`** (local generator),
  or the cloud's deployed generator is used and nothing changes.

- **`EventLogManager` de-JUL (runtime fix).** Regenerate with `-Dcap.runtimeAudit=true`
  so the SEP uses the real `addEventAudit(...)` instead of the `WasmEventLogManager`
  shim. Then build: compiles + audit ✓ = fixed; build fails on `java.util.logging`
  (or `ForkedTriggerTask`/ForkJoin) = not yet. (Runtime-only, so generation source
  doesn't matter — but you still bump `<fluxtion.version>` so the fixed runtime is
  what TeaVM compiles.)

The default build (no profile, no toggle) stays on released 1.0.7 + cloud generation
+ the JUL-free shim, and remains green.
