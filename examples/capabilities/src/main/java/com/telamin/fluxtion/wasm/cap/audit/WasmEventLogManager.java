package com.telamin.fluxtion.wasm.cap.audit;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.audit.Auditor;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.audit.LogRecordListener;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.time.Clock;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ObjLongConsumer;

/**
 * A WASM-compatible reimplementation of fluxtion-runtime's {@code EventLogManager}
 * auditor. Behaviourally identical, but with the {@code java.util.logging}
 * dependency removed (no static JUL {@code Logger}, no {@code JULLogRecordListener}
 * default) so it compiles under TeaVM/WASM. The log sink defaults to a no-op; the
 * host registers a real {@link LogRecordListener} at runtime via
 * {@link EventLogControlEvent}.
 *
 * <p>Registered as a normal auditor — {@code config.addAuditor(new WasmEventLogManager()
 * .tracingOn(level), "eventLogger")} — which is all {@code addEventAudit(...)} does
 * on the JVM, minus the JUL.
 */
public class WasmEventLogManager implements Auditor {

    private LogRecordListener sink = l -> {
    };
    private LogRecord logRecord;
    private Map<String, EventLogger> node2Logger;
    private Map<String, EventLogSource> name2LogSourceMap;
    private boolean clearAfterPublish;
    private boolean trace = false;
    private boolean printEventToString = true;
    private boolean printThreadName = false;
    private LogLevel traceLevel = LogLevel.NONE;
    private boolean canTrace = false;

    @Inject
    public Clock clock;

    public WasmEventLogManager tracingOn(LogLevel level) {
        this.trace = level != LogLevel.NONE;
        this.traceLevel = level;
        return this;
    }

    public WasmEventLogManager tracingOff() {
        this.trace = false;
        this.traceLevel = LogLevel.NONE;
        return this;
    }

    public void setLogSink(LogRecordListener sink) {
        this.sink = sink;
    }

    @Override
    public void init() {
        logRecord = new LogRecord(clock);
        logRecord.printEventToString(printEventToString);
        logRecord.setPrintThreadName(printThreadName);
        node2Logger = new HashMap<>();
        name2LogSourceMap = new HashMap<>();
        clearAfterPublish = true;
    }

    @Override
    public void nodeRegistered(Object node, String nodeName) {
        EventLogger logger = new EventLogger(logRecord, nodeName);
        if (node instanceof EventLogSource) {
            EventLogSource calcSource = (EventLogSource) node;
            calcSource.setLogger(logger);
            name2LogSourceMap.put(nodeName, calcSource);
        }
        node2Logger.put(nodeName, logger);
        recomputeCanTrace();
    }

    @Override
    public boolean auditInvocations() {
        return trace;
    }

    @Override
    public void nodeInvoked(Object node, String nodeName, String methodName, Object event) {
        // single-threaded WASM: no ForkedTriggerTask / thread-name (both pull
        // WASM-hostile classes — ForkJoin); just record the method invocation.
        EventLogger logger = node2Logger.getOrDefault(nodeName, NullEventLogger.INSTANCE);
        logger.logNodeInvocation(traceLevel);
        logger.log("method", methodName, traceLevel);
    }

    @OnEventHandler(propagate = false)
    public void calculationLogConfig(EventLogControlEvent newConfig) {
        if (newConfig.getLogRecordProcessor() != null) {
            this.sink = newConfig.getLogRecordProcessor();
        }
        // (the runtime EventLogManager also supports swapping the LogRecord here via
        //  the package-private LogRecord.sb buffer — omitted; not needed for WASM audit)
        final LogLevel level = newConfig.getLevel();
        if (level != null
                && (logRecord.groupingId == null || logRecord.groupingId.equals(newConfig.getGroupId()))) {
            node2Logger.computeIfPresent(newConfig.getSourceId(), (t, u) -> {
                u.setLevel(level);
                return u;
            });
            if (newConfig.getSourceId() == null) {
                node2Logger.values().forEach(t -> t.setLevel(newConfig.getLevel()));
            }
        }
        final ObjLongConsumer<StringBuilder> timeFormatter = newConfig.getTimeFormatter();
        if (timeFormatter != null) {
            logRecord.setTimeFormatter(timeFormatter);
        }
        recomputeCanTrace();
    }

    @Override
    public void processingComplete() {
        if (canTrace | logRecord.terminateRecord()) {
            sink.processLogRecord(logRecord);
        }
        if (clearAfterPublish) {
            logRecord.clear();
        }
    }

    @Override
    public void eventReceived(Event triggerEvent) {
        logRecord.triggerEvent(triggerEvent);
    }

    @Override
    public void eventReceived(Object triggerEvent) {
        logRecord.triggerObject(triggerEvent);
    }

    private void updateLogRecord() {
        for (Map.Entry<String, EventLogSource> entry : name2LogSourceMap.entrySet()) {
            String nodeName = entry.getKey();
            EventLogSource calcSource = entry.getValue();
            EventLogger logger = new EventLogger(logRecord, nodeName);
            calcSource.setLogger(logger);
            node2Logger.put(nodeName, logger);
        }
    }

    private void recomputeCanTrace() {
        canTrace = trace && node2Logger.values().stream().anyMatch(e -> e.canLog(traceLevel));
    }
}
