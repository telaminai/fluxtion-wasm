package com.telamin.fluxtion.wasm.cap.app;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.callback.EventDispatcher;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;
import com.telamin.fluxtion.wasm.bootstrap.MiniJson;

/** Decodes a {@code type="price"} event and re-injects a typed {@link PriceTick}. */
public class PriceNode {

    @Inject
    @NoTriggerReference
    public EventDispatcher eventDispatcher;

    @OnEventHandler(filterString = "price")
    public boolean onPrice(StringEvent e) {
        String json = e.payload();
        String px = MiniJson.string(json, "price");
        double price = px == null || px.isEmpty() ? 0d : Double.parseDouble(px);
        eventDispatcher.processReentrantEvent(new PriceTick(MiniJson.string(json, "symbol"), price));
        return false;
    }
}
