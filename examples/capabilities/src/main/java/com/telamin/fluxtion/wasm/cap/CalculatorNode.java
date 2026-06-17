package com.telamin.fluxtion.wasm.cap;

import com.telamin.fluxtion.runtime.annotations.ExportService;

/**
 * Exported-service probe. The SEP exports {@link Calculator}; the host calls it
 * directly via {@code sep.getExportedService(Calculator.class)}. Whether the
 * exported-service dispatch survives WASM is the capability under test.
 */
public class CalculatorNode implements @ExportService Calculator {

    private transient int total;

    @Override
    public void add(int a, int b) {
        total = a + b;
    }

    /** Not part of the exported interface — read via getNodeById to verify the call landed. */
    public int getTotal() {
        return total;
    }
}
