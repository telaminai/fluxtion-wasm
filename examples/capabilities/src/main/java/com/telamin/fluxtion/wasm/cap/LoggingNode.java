package com.telamin.fluxtion.wasm.cap;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * Audit-logging probe. Extends {@link EventLogNode} to get {@code auditLog}, and
 * writes structured audit entries when it handles a String event. With a
 * {@code WasmEventLogManager} auditor wired in (JUL-free), these entries reach the
 * registered {@code LogRecordListener} in WASM.
 */
public class LoggingNode extends EventLogNode {

    @OnEventHandler
    public boolean onString(String event) {
        auditLog.info("received", event);
        auditLog.info("length", event.length());
        return true;
    }
}
