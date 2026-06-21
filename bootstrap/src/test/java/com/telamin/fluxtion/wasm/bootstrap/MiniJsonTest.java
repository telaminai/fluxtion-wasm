package com.telamin.fluxtion.wasm.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the flat-object field readers used by the WASM JSON bridge + generated decoders. */
class MiniJsonTest {

    private static final String JSON = "{\"type\":\"PriceUpdate\",\"symbol\":\"EURUSD\",\"quantity\":100,"
            + "\"price\":1.2345,\"big\":9000000000,\"active\":true}";

    @Test
    void string_readsValueAndSkipsAKeyThatAppearsAsAValue() {
        assertEquals("PriceUpdate", MiniJson.string(JSON, "type"));
        assertEquals("EURUSD", MiniJson.string(JSON, "symbol"));
        // "PriceUpdate" is the value of "type"; searching for it as a key must not match
        assertNull(MiniJson.string(JSON, "PriceUpdate"));
        assertNull(MiniJson.string(JSON, "missing"));
        assertNull(MiniJson.string(null, "type"));
    }

    @Test
    void intField_parsesOrDefaults() {
        assertEquals(100, MiniJson.intField(JSON, "quantity", 0));
        assertEquals(-1, MiniJson.intField(JSON, "missing", -1));
        assertEquals(7, MiniJson.intField(JSON, "symbol", 7)); // non-numeric -> default
    }

    @Test
    void longField_parsesValuesBeyondInt() {
        assertEquals(9000000000L, MiniJson.longField(JSON, "big", 0L));
        assertEquals(5L, MiniJson.longField(JSON, "missing", 5L));
    }

    @Test
    void doubleField_parses() {
        assertEquals(1.2345, MiniJson.doubleField(JSON, "price", 0), 1e-9);
        assertEquals(2.5, MiniJson.doubleField(JSON, "missing", 2.5), 1e-9);
    }

    @Test
    void boolField_parses() {
        assertTrue(MiniJson.boolField(JSON, "active", false));
        assertFalse(MiniJson.boolField(JSON, "missing", false));
        assertTrue(MiniJson.boolField("{\"flag\":1}", "flag", false));
    }
}
