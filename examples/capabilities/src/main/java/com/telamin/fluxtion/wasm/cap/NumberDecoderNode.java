package com.telamin.fluxtion.wasm.cap;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.callback.EventDispatcher;
import com.telamin.fluxtion.wasm.bootstrap.MiniJson;

/**
 * Edge decoder for {@code {"type":"number","value":N}}: it re-injects a typed
 * {@link Integer} so the Integer-typed DSL flows (the {@code dsl} pipeline and the
 * {@code "doubled"} streamed flow) can be driven through the generic JSON bridge —
 * the same {@code JsonDecoderNode} pattern, for a primitive. Without it those flows
 * would need a bespoke typed host method; with it, {@code sep.onEvent({type:'number',
 * value:5})} reaches them, keeping one consistent JS path.
 */
public class NumberDecoderNode {

    @Inject
    @NoTriggerReference
    public EventDispatcher eventDispatcher;

    @OnEventHandler(filterString = "number")
    public boolean onNumber(StringEvent e) {
        int value = MiniJson.intField(e.payload(), "value", 0);
        eventDispatcher.processReentrantEvent(Integer.valueOf(value));   // typed Integer into the graph
        return false;
    }
}
