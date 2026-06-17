package com.telamin.fluxtion.wasm.cap;

/**
 * An exported service interface — the SEP exposes this so the host can call into
 * it directly. Exported-service methods are dispatch methods (void or boolean),
 * not queries — read any resulting state separately (here via getNodeById).
 */
public interface Calculator {

    void add(int a, int b);
}
