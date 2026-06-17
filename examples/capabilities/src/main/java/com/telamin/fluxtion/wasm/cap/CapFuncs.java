package com.telamin.fluxtion.wasm.cap;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;

/** DSL functions referenced by the capability graph (closed-world safe). */
public final class CapFuncs {

    private CapFuncs() {
    }

    public static Integer times2(Integer x) {
        return x * 2;
    }

    public static boolean positive(Integer x) {
        return x > 0;
    }

    // ── StringEvent → named-sink converters (for eventToSink). Static refs = closed-world safe ──
    public static boolean isTrade(StringEvent e) {
        return "trade".equals(e.filterString());
    }

    public static boolean isQuote(StringEvent e) {
        return "quote".equals(e.filterString());
    }

    public static String toTrade(StringEvent e) {
        String[] p = e.payload().split(":");
        return "Trade{symbol=" + p[0] + ", qty=" + Integer.parseInt(p[1]) + "}";
    }

    public static String toQuote(StringEvent e) {
        String[] p = e.payload().split(":");
        return "Quote{symbol=" + p[0] + ", price=" + Double.parseDouble(p[1]) + "}";
    }

    // ── typed → JSON egress: a Trade (re-injected by the edge decoder) mapped to a
    //    JSON string for a named sink, so the generic JSON bridge returns JSON ──
    public static String tradeToJson(Trade t) {
        return "{\"symbol\":\"" + t.symbol() + "\",\"qty\":" + t.qty() + "}";
    }
}
