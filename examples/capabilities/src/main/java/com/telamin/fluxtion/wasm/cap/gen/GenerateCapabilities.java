package com.telamin.fluxtion.wasm.cap.gen;

import com.telamin.fluxtion.Fluxtion;
import com.telamin.fluxtion.builder.DataFlowBuilder;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.wasm.cap.CalculatorNode;
import com.telamin.fluxtion.wasm.cap.CallbackReceiver;
import com.telamin.fluxtion.wasm.cap.CapFuncs;
import com.telamin.fluxtion.wasm.cap.GreeterConsumer;
import com.telamin.fluxtion.wasm.cap.JsonDecoderNode;
import com.telamin.fluxtion.wasm.cap.LoggingNode;
import com.telamin.fluxtion.wasm.cap.NumberDecoderNode;
import com.telamin.fluxtion.wasm.cap.PriceLookupNode;
import com.telamin.fluxtion.wasm.cap.StringConverter;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;
import com.telamin.fluxtion.wasm.cap.Trade;
import com.telamin.fluxtion.wasm.cap.TradeHandler;
import com.telamin.fluxtion.wasm.cap.app.Accepted;
import com.telamin.fluxtion.wasm.cap.app.AppFuncs;
import com.telamin.fluxtion.wasm.cap.app.OrderRiskNode;
import com.telamin.fluxtion.wasm.cap.app.PriceNode;
import com.telamin.fluxtion.wasm.cap.app.PriceTick;
import com.telamin.fluxtion.wasm.cap.app.Rejected;

/**
 * Regenerates the capability SEP. Run manually:
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.telamin.fluxtion.wasm.cap.gen.GenerateCapabilities
 * </pre>
 * then copy target/generated-sources/fluxtion/.../CapabilitiesProcessor.java into
 * src/main/java and commit.
 *
 * <p>The SEP exercises several capabilities at once so one host can probe them:
 * a DSL pipeline (map/filter → sink), a streamed flow ({@code .id("doubled")}),
 * an audit-logging node, and event auditing enabled at DEBUG.
 */
public class GenerateCapabilities {

    public static void main(String[] args) {
        String outDir = args.length > 0 ? args[0] : "target/generated-sources/fluxtion";
        Fluxtion.compile(
                cfg -> {
                    // DSL pipeline + sink egress
                    DataFlowBuilder.subscribe(Integer.class)
                            .map(CapFuncs::times2)
                            .filter(CapFuncs::positive)
                            .sink("dslOut");
                    // a streamed flow read back via getStreamed("doubled")
                    DataFlowBuilder.subscribe(Integer.class)
                            .map(CapFuncs::times2)
                            .id("doubled");
                    // a signal subscription → sink (probe: publishSignal)
                    DataFlowBuilder.subscribeToSignal("greet", String.class)
                            .sink("signalOut");
                    // NAMED sinks — Java names them; JS reads any via eventToSink(...).
                    // a filtered StringEvent is decoded by a DSL flow into a named sink.
                    DataFlowBuilder.subscribe(StringEvent.class)
                            .filter(CapFuncs::isTrade).map(CapFuncs::toTrade).sink("trades");
                    DataFlowBuilder.subscribe(StringEvent.class)
                            .filter(CapFuncs::isQuote).map(CapFuncs::toQuote).sink("quotes");
                    // an exported service (probe: getExportedService)
                    cfg.addNode(new CalculatorNode(), "calc");
                    // a node with @ServiceRegistered (probe: injected service)
                    cfg.addNode(new GreeterConsumer(), "greeterConsumer");
                    // a node receiving a callback delivered as an event (probe: callback-via-event)
                    cfg.addNode(new CallbackReceiver(), "callbackReceiver");
                    // converter nodes — generic onEventString(filter, payload) routes here by
                    // filter and decodes the string into a typed result inside the graph
                    cfg.addNode(new StringConverter(), "stringConverter");
                    // a node that queries a JS-implemented service (probe: js-implements-a-service)
                    cfg.addNode(new PriceLookupNode(), "priceLookup");
                    // edge decoder: json string → typed Trade re-injected into the graph;
                    // tradeHandler is a standard typed @OnEventHandler downstream
                    cfg.addNode(new JsonDecoderNode(), "jsonDecoder");
                    cfg.addNode(new TradeHandler(), "tradeHandler");
                    // edge decoder for {"type":"number","value":N} → re-inject Integer,
                    // so the Integer DSL flows are reachable through the generic JSON bridge
                    cfg.addNode(new NumberDecoderNode(), "numberDecoder");
                    // ── Live Order Desk app graph (driven by OrderDeskHost) ──
                    // order/price events → risk gate (reads+stores position via a JS-backed
                    // service) → typed Accepted/Rejected/PriceTick re-injected → JSON sinks
                    cfg.addNode(new OrderRiskNode(), "orderRisk");
                    cfg.addNode(new PriceNode(), "priceNode");
                    DataFlowBuilder.subscribe(Accepted.class)
                            .map(AppFuncs::acceptedJson).sink("accepted");
                    DataFlowBuilder.subscribe(Rejected.class)
                            .map(AppFuncs::rejectedJson).sink("rejected");
                    DataFlowBuilder.subscribe(PriceTick.class)
                            .map(AppFuncs::priceJson).sink("marketData");
                    // typed → JSON egress: the re-injected Trade is mapped to a JSON
                    // string and pushed to the "tradeOut" sink, so the generic JSON
                    // bridge can return JSON for a json-typed event (json in → json out).
                    DataFlowBuilder.subscribe(Trade.class)
                            .map(CapFuncs::tradeToJson).sink("tradeOut");
                    // an audit-logging node
                    cfg.addNode(new LoggingNode(), "logger");
                    // Audit: the REAL runtime EventLogManager via addEventAudit. De-JUL'd in
                    // fluxtion 1.0.8 (static Logger + JULLogRecordListener default), so it compiles
                    // + runs in WASM. Requires fluxtion >= 1.0.8 (we're on 1.0.9).
                    cfg.addEventAudit(LogLevel.INFO);
                },
                compilerCfg -> compilerCfg
                        .packageName("com.telamin.fluxtion.wasm.cap.generated")
                        .className("CapabilitiesProcessor")
                        .outputDirectory(outDir)
                        .writeSourceToFile(true)
                        .copySourceToResourcesDirectory(false)
                        .compileSource(false)
                        // emit the WASM host bundle (JsonHost + Main + ReflectionSupplier + SPI)
                        // instead of hand-writing it — fluxtion >= 1.0.9, generator >= 1.0.49.
                        .generateWasmHost(true));
        System.out.println("[gen] CapabilitiesProcessor written under " + outDir);
    }
}
