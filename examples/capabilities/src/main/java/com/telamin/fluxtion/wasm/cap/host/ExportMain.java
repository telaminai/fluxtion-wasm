package com.telamin.fluxtion.wasm.cap.host;

import com.telamin.fluxtion.wasm.cap.generated.JsonHost;
import org.teavm.jso.JSExportClasses;

/**
 * Registers the exported classes for WASM-GC. main() is intentionally empty —
 * the capability probes are driven from JS via {@link CapHost}, the generic
 * JSON value bridge via {@link JsonHost} (the generator-emitted host in the SEP
 * package — see GenerateCapabilities.generateWasmHost), and the Live Order Desk
 * app via {@link OrderDeskHost}.
 */
@JSExportClasses({CapHost.class, JsonHost.class, OrderDeskHost.class})
public class ExportMain {
    public static void main(String[] args) {
        // exports-only entry point
    }
}
