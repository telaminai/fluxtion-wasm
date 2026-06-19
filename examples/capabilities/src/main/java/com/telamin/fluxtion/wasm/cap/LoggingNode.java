package com.telamin.fluxtion.wasm.cap;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * Audit-logging probe. Extends {@link EventLogNode} to get {@code auditLog}, and
 * writes structured audit entries when it handles a String event. With the runtime
 * {@code EventLogManager} auditor wired in (de-JUL'd in fluxtion 1.0.8), these entries
 * reach the registered {@code LogRecordListener} in WASM.
 */
public class LoggingNode extends EventLogNode {

    @OnEventHandler
    public boolean onString(String event) {
        auditLog.info("received", event);
        auditLog.info("length", event.length());
        return true;
    }
}
