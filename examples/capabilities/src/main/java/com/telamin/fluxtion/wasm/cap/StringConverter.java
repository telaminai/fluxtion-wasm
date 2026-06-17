package com.telamin.fluxtion.wasm.cap;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;

/**
 * Converter nodes inside the graph. A generic {@link StringEvent} is routed here
 * by its {@code filter} (Fluxtion's {@code @OnEventHandler(filterString=...)}
 * dispatch), and each handler decodes the string payload into a typed result —
 * so one generic {@code onEventString(filter, payload)} feeds many typed events
 * without any per-SEP exported method.
 */
public class StringConverter {

    private transient String last = "none";

    @OnEventHandler(filterString = "trade")
    public boolean onTrade(StringEvent e) {
        String[] p = e.payload().split(":");
        last = "Trade{symbol=" + p[0] + ", qty=" + Integer.parseInt(p[1]) + "}";
        return true;
    }

    @OnEventHandler(filterString = "quote")
    public boolean onQuote(StringEvent e) {
        String[] p = e.payload().split(":");
        last = "Quote{symbol=" + p[0] + ", price=" + Double.parseDouble(p[1]) + "}";
        return true;
    }

    public String last() {
        return last;
    }
}
