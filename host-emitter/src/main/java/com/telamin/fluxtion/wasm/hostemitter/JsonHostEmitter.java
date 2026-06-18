package com.telamin.fluxtion.wasm.hostemitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits the hand-written WASM host boilerplate a TeaVM-compiled Fluxtion SEP needs,
 * so the generator can produce it instead of a developer copying the template.
 *
 * <p>Two files come out, parameterized only by the SEP's package, the processor's
 * fully-qualified name and a host class name (default {@code JsonHost}):
 * <ul>
 *   <li>{@code {host}.java} — the {@code @JSExport} shell delegating every verb to the
 *       reusable {@code JsonBridgeHost}; byte-identical (modulo the processor name) to
 *       the proven hand-written {@code JsonHost} in {@code examples/capabilities}.</li>
 *   <li>{@code {host}Main.java} — the empty {@code main()} carrying {@code @JSExportClasses}
 *       so TeaVM exports the shell (TeaVM does not export inherited methods, which is
 *       exactly why this is generated source, not a base class).</li>
 * </ul>
 *
 * <p>This is pure string templating — no graph analysis beyond the processor name — and
 * has ZERO dependency on the compiler, so it can be unit-tested as a golden against the
 * checked-in capabilities host before {@code fluxtion-compiler} wires it into the
 * {@code generateWasmHost(true)} path.
 */
public final class JsonHostEmitter {

    /**
     * The only inputs the host template needs.
     *
     * @param packageName  package of the emitted host (usually the SEP's package)
     * @param processorFqn fully-qualified name of the generated SEP, e.g.
     *                     {@code com.acme.generated.MyProcessor}
     * @param hostClassName simple name of the emitted host (default {@code "JsonHost"})
     */
    public record Spec(String packageName, String processorFqn, String hostClassName) {
        public Spec {
            require(packageName, "packageName");
            require(processorFqn, "processorFqn");
            require(hostClassName, "hostClassName");
        }

        /** Convenience: default the host class name to {@code JsonHost}. */
        public Spec(String packageName, String processorFqn) {
            this(packageName, processorFqn, "JsonHost");
        }

        String processorSimpleName() {
            int dot = processorFqn.lastIndexOf('.');
            return dot < 0 ? processorFqn : processorFqn.substring(dot + 1);
        }

        private static void require(String v, String name) {
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException(name + " must be set");
            }
        }
    }

    /**
     * @return a map of {@code simpleFileName -> Java source}: the host shell and its
     *         {@code @JSExportClasses} Main, in the order they should be written.
     */
    public Map<String, String> emit(Spec spec) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(spec.hostClassName() + ".java", host(spec));
        out.put(spec.hostClassName() + "Main.java", main(spec));
        return out;
    }

    private String host(Spec spec) {
        return HOST_TEMPLATE
                .replace("__PKG__", spec.packageName())
                .replace("__PROC_FQN__", spec.processorFqn())
                .replace("__PROC__", spec.processorSimpleName())
                .replace("__HOST__", spec.hostClassName());
    }

    private String main(Spec spec) {
        return MAIN_TEMPLATE
                .replace("__PKG__", spec.packageName())
                .replace("__HOST__", spec.hostClassName());
    }

    // The proven shape — see examples/capabilities .../host/JsonHost.java (golden).
    private static final String HOST_TEMPLATE = """
            package __PKG__;

            import com.telamin.fluxtion.runtime.DataFlow;
            import com.telamin.fluxtion.wasm.bootstrap.JsonBridgeHost;
            import com.telamin.fluxtion.wasm.bootstrap.SinkListener;
            import __PROC_FQN__;
            import org.teavm.jso.JSExport;

            /**
             * The WASM export shell for the generic JSON bridge. This is the ONLY per-SEP code
             * the bridge needs: a fixed {@code @JSExport} surface delegating to the reusable
             * {@link JsonBridgeHost}, plus a constructor that names the concrete SEP. Every
             * method here is identical for any SEP — only {@link #initSep()} mentions the
             * processor class, so this shell is a template (later generator-emitted).
             *
             * <p>From JS: {@code const h = proc.newHost('__HOST__'); h.onEventJson('{"type":"json",...}');}
             * — or, more ergonomically, via the runtime package's {@code proc.jsonBridge('__HOST__')}
             * wrapper which marshals plain JS objects for you.
             */
            public class __HOST__ {

                private final JsonBridgeHost bridge;

                @JSExport
                public __HOST__() {
                    this.bridge = new JsonBridgeHost(initSep());
                }

                /** The only SEP-specific line: construct + init the concrete processor. */
                private static DataFlow initSep() {
                    __PROC__ sep = new __PROC__();
                    sep.init();
                    return sep;
                }

                @JSExport
                public void onEventJson(String json) {
                    bridge.onEventJson(json);
                }

                @JSExport
                public void onEvent(String type, String payload) {
                    bridge.onEvent(type, payload);
                }

                @JSExport
                public void addSink(String sinkId) {
                    bridge.addSink(sinkId);
                }

                @JSExport
                public String lastSink(String sinkId) {
                    return bridge.lastSink(sinkId);
                }

                @JSExport
                public String getStreamed(String flowId) {
                    return bridge.getStreamed(flowId);
                }

                @JSExport
                public void subscribe(String sinkId, SinkListener listener) {
                    bridge.subscribe(sinkId, listener);
                }

                @JSExport
                public void signal(String name, String value) {
                    bridge.signal(name, value);
                }

                @JSExport
                public String drainAudit() {
                    return bridge.drainAudit();
                }

                @JSExport
                public void setLogLevel(String level) {
                    bridge.setLogLevel(level);
                }
            }
            """;

    private static final String MAIN_TEMPLATE = """
            package __PKG__;

            import org.teavm.jso.JSExportClasses;

            /**
             * Registers the exported host class for WASM-GC. main() is intentionally empty —
             * the SEP is driven from JS via {@link __HOST__}. Generated alongside the host;
             * TeaVM only exports classes named here, and only their own (non-inherited)
             * {@code @JSExport} methods, so the host must be a concrete, listed class.
             */
            @JSExportClasses({__HOST__.class})
            public class __HOST__Main {
                public static void main(String[] args) {
                    // exports-only entry point
                }
            }
            """;
}
