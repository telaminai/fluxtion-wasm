package com.telamin.fluxtion.wasm.cap;

import org.junit.Test;

import java.util.function.Supplier;

/**
 * JVM baseline probe — runs each capability probe and prints ✅/❌. Not a pass/fail
 * assertion: it records what the JVM does so the WASM probe (Node / browser) can be
 * compared against it. Keep it informative.
 */
public class CapabilityProbeJvmTest {

    @Test
    public void probeAll() {
        CapabilitiesHost h = new CapabilitiesHost();
        probe("dsl", () -> String.valueOf(h.dsl(5)));
        probe("getStreamed", () -> h.getStreamed(7));
        probe("audit", () -> h.audit("hello"));
        probe("setLogLevel", () -> h.setLogLevel("TRACE"));
        probe("signal", () -> h.signal("world"));
        probe("exportedService", () -> h.exportedService(3, 4));
        probe("injectedService", () -> h.injectedService("world"));
        probe("callbackViaEvent", () -> h.callbackViaEvent("world"));
        probe("triggerCalc", h::triggerCalc);
    }

    private static void probe(String name, Supplier<String> action) {
        try {
            System.out.println("[JVM] OK   " + name + " -> " + oneLine(action.get()));
        } catch (Throwable t) {
            System.out.println("[JVM] FAIL " + name + " -> " + t.getMessage());
        }
    }

    private static String oneLine(String s) {
        String t = s.replace("\n", " | ");
        return t.length() > 120 ? t.substring(0, 120) + "…" : t;
    }
}
