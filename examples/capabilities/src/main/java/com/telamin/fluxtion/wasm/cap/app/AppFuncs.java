package com.telamin.fluxtion.wasm.cap.app;

/** Order-desk DSL mappers — plain statics (closed-world safe), typed event → JSON for a sink. */
public final class AppFuncs {

    private AppFuncs() {
    }

    public static String acceptedJson(Accepted a) {
        return "{\"symbol\":\"" + a.symbol() + "\",\"qty\":" + a.qty() + ",\"position\":" + a.position() + "}";
    }

    public static String rejectedJson(Rejected r) {
        return "{\"symbol\":\"" + r.symbol() + "\",\"qty\":" + r.qty() + ",\"reason\":\"" + r.reason() + "\"}";
    }

    public static String priceJson(PriceTick p) {
        return "{\"symbol\":\"" + p.symbol() + "\",\"price\":" + p.price() + "}";
    }
}
