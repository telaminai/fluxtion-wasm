/*
 * Copyright: © 2025.  Gregory Higgins <greg.higgins@v12technology.com> - All Rights Reserved
 * This source code is protected under international copyright law.  All rights
 * reserved and protected by the copyright holders.
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holders.  If you encounter this file and do not have
 * permission, please contact the copyright holders and delete this file.
 */
package com.telamin.fluxtion.wasm.cap.generated;

import com.telamin.fluxtion.runtime.CloneableDataFlow;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.annotations.ExportService;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.Auditor;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.telamin.fluxtion.runtime.audit.EventLogManager;
import com.telamin.fluxtion.runtime.audit.NodeNameAuditor;
import com.telamin.fluxtion.runtime.callback.CallbackDispatcherImpl;
import com.telamin.fluxtion.runtime.callback.ExportFunctionAuditEvent;
import com.telamin.fluxtion.runtime.callback.InternalEventProcessor;
import com.telamin.fluxtion.runtime.context.DataFlowContext;
import com.telamin.fluxtion.runtime.event.Event;
import com.telamin.fluxtion.runtime.event.Signal;
import com.telamin.fluxtion.runtime.flowfunction.function.FilterFlowFunction;
import com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction.MapRef2RefFlowFunction;
import com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction;
import com.telamin.fluxtion.runtime.input.EventFeed;
import com.telamin.fluxtion.runtime.input.SubscriptionManager;
import com.telamin.fluxtion.runtime.input.SubscriptionManagerNode;
import com.telamin.fluxtion.runtime.lifecycle.BatchHandler;
import com.telamin.fluxtion.runtime.lifecycle.Lifecycle;
import com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode;
import com.telamin.fluxtion.runtime.node.ForkedTriggerTask;
import com.telamin.fluxtion.runtime.node.MutableDataFlowContext;
import com.telamin.fluxtion.runtime.output.SinkDeregister;
import com.telamin.fluxtion.runtime.output.SinkPublisher;
import com.telamin.fluxtion.runtime.output.SinkRegistration;
import com.telamin.fluxtion.runtime.service.ServiceListener;
import com.telamin.fluxtion.runtime.service.ServiceRegistryNode;
import com.telamin.fluxtion.runtime.time.Clock;
import com.telamin.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent;
import com.telamin.fluxtion.wasm.cap.Calculator;
import com.telamin.fluxtion.wasm.cap.CalculatorNode;
import com.telamin.fluxtion.wasm.cap.CallbackReceiver;
import com.telamin.fluxtion.wasm.cap.CapFuncs;
import com.telamin.fluxtion.wasm.cap.GreeterConsumer;
import com.telamin.fluxtion.wasm.cap.JsonDecoderNode;
import com.telamin.fluxtion.wasm.cap.LoggingNode;
import com.telamin.fluxtion.wasm.cap.NumberDecoderNode;
import com.telamin.fluxtion.wasm.cap.PriceLookupNode;
import com.telamin.fluxtion.wasm.cap.ServiceEvent;
import com.telamin.fluxtion.wasm.cap.StringConverter;
import com.telamin.fluxtion.wasm.bootstrap.StringEvent;
import com.telamin.fluxtion.wasm.cap.SymbolEvent;
import com.telamin.fluxtion.wasm.cap.Trade;
import com.telamin.fluxtion.wasm.cap.TradeHandler;
import com.telamin.fluxtion.wasm.cap.app.Accepted;
import com.telamin.fluxtion.wasm.cap.app.AppFuncs;
import com.telamin.fluxtion.wasm.cap.app.OrderRiskNode;
import com.telamin.fluxtion.wasm.cap.app.PriceNode;
import com.telamin.fluxtion.wasm.cap.app.PriceTick;
import com.telamin.fluxtion.wasm.cap.app.Rejected;
import java.io.File;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 *
 *
 * <pre>
 * generation time           : Not available
 * api version               : unknown api version
 * analyser version          : unknown analyser version
 * target generator version  : unknown generator version
 * </pre>
 *
 * Event classes supported:
 *
 * <ul>
 *   <li>com.telamin.fluxtion.runtime.audit.EventLogControlEvent
 *   <li>com.telamin.fluxtion.runtime.event.Signal
 *   <li>com.telamin.fluxtion.runtime.output.SinkDeregister
 *   <li>com.telamin.fluxtion.runtime.output.SinkRegistration
 *   <li>com.telamin.fluxtion.runtime.time.ClockStrategy.ClockStrategyEvent
 *   <li>com.telamin.fluxtion.wasm.cap.ServiceEvent
 *   <li>com.telamin.fluxtion.wasm.bootstrap.StringEvent
 *   <li>com.telamin.fluxtion.wasm.cap.SymbolEvent
 *   <li>com.telamin.fluxtion.wasm.cap.Trade
 *   <li>com.telamin.fluxtion.wasm.cap.app.Accepted
 *   <li>com.telamin.fluxtion.wasm.cap.app.PriceTick
 *   <li>com.telamin.fluxtion.wasm.cap.app.Rejected
 *   <li>java.lang.Integer
 *   <li>java.lang.String
 * </ul>
 *
 * @author Greg Higgins
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class CapabilitiesProcessor
    implements CloneableDataFlow<CapabilitiesProcessor>,
        /*--- @ExportService start ---*/
        @ExportService Calculator,
        @ExportService ServiceListener,
        /*--- @ExportService end ---*/
        DataFlow,
        InternalEventProcessor,
        BatchHandler {

  //Node declarations
  private final transient CallbackDispatcherImpl callbackDispatcher = new CallbackDispatcherImpl();
  public final transient Clock clock = new Clock();
  public final transient EventLogManager eventLogger = new EventLogManager();
  public final transient JsonDecoderNode jsonDecoder = new JsonDecoderNode();
  public final transient NodeNameAuditor nodeNameLookup = new NodeNameAuditor();
  public final transient NumberDecoderNode numberDecoder = new NumberDecoderNode();
  public final transient OrderRiskNode orderRisk = new OrderRiskNode();
  public final transient PriceNode priceNode = new PriceNode();
  private final transient SubscriptionManagerNode subscriptionManager =
      new SubscriptionManagerNode();
  private final transient MutableDataFlowContext context =
      new com.telamin.fluxtion.runtime.node.MutableDataFlowContext(
          nodeNameLookup, callbackDispatcher, subscriptionManager, callbackDispatcher);;
  private final transient SinkPublisher accepted =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("accepted");;
  private final transient SinkPublisher dslOut =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("dslOut");;
  private final transient DefaultEventHandlerNode handlerAccepted =
      new com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.telamin.fluxtion.wasm.cap.app.Accepted.class,
          "handlerAccepted",
          context);;
  private final transient DefaultEventHandlerNode handlerInteger =
      new com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode<>(
          2147483647, "", java.lang.Integer.class, "handlerInteger", context);;
  public final transient MapRef2RefFlowFunction doubled =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          handlerInteger,
          CapFuncs::times2,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->times2", false, null, false));;
  private final transient DefaultEventHandlerNode handlerPriceTick =
      new com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.telamin.fluxtion.wasm.cap.app.PriceTick.class,
          "handlerPriceTick",
          context);;
  private final transient DefaultEventHandlerNode handlerRejected =
      new com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.telamin.fluxtion.wasm.cap.app.Rejected.class,
          "handlerRejected",
          context);;
  private final transient DefaultEventHandlerNode handlerSignal_greet =
      new com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode<>(
          2147483647,
          "greet",
          com.telamin.fluxtion.runtime.event.Signal.class,
          "handlerSignal_greet",
          context);;
  private final transient DefaultEventHandlerNode handlerStringEvent =
      new com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode<>(
          2147483647,
          "",
          com.telamin.fluxtion.wasm.bootstrap.StringEvent.class,
          "handlerStringEvent",
          context);;
  private final transient FilterFlowFunction filterFlowFunction_17 =
      new com.telamin.fluxtion.runtime.flowfunction.function.FilterFlowFunction<>(
          handlerStringEvent,
          CapFuncs::isTrade,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->isTrade", false, null, false));;
  private final transient FilterFlowFunction filterFlowFunction_20 =
      new com.telamin.fluxtion.runtime.flowfunction.function.FilterFlowFunction<>(
          handlerStringEvent,
          CapFuncs::isQuote,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->isQuote", false, null, false));;
  private final transient DefaultEventHandlerNode handlerTrade =
      new com.telamin.fluxtion.runtime.node.DefaultEventHandlerNode<>(
          2147483647, "", com.telamin.fluxtion.wasm.cap.Trade.class, "handlerTrade", context);;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_11 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          handlerInteger,
          CapFuncs::times2,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->times2", false, null, false));;
  private final transient FilterFlowFunction filterFlowFunction_12 =
      new com.telamin.fluxtion.runtime.flowfunction.function.FilterFlowFunction<>(
          mapRef2RefFlowFunction_11,
          CapFuncs::positive,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->positive", false, null, false));;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_15 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          handlerSignal_greet,
          Signal<Object>::getValue,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "Signal->getValue", false, null, false));;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_18 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          filterFlowFunction_17,
          CapFuncs::toTrade,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->toTrade", false, null, false));;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_21 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          filterFlowFunction_20,
          CapFuncs::toQuote,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->toQuote", false, null, false));;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_23 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          handlerAccepted,
          AppFuncs::acceptedJson,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "AppFuncs->acceptedJson", false, null, false));;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_25 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          handlerRejected,
          AppFuncs::rejectedJson,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "AppFuncs->rejectedJson", false, null, false));;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_27 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          handlerPriceTick,
          AppFuncs::priceJson,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "AppFuncs->priceJson", false, null, false));;
  private final transient MapRef2RefFlowFunction mapRef2RefFlowFunction_29 =
      new com.telamin.fluxtion.runtime.flowfunction.function.MapFlowFunction
          .MapRef2RefFlowFunction<>(
          handlerTrade,
          CapFuncs::tradeToJson,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "CapFuncs->tradeToJson", false, null, false));;
  private final transient SinkPublisher marketData =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("marketData");;
  private final transient PushFlowFunction pushFlowFunction_13 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          filterFlowFunction_12,
          dslOut::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  private final transient PushFlowFunction pushFlowFunction_24 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          mapRef2RefFlowFunction_23,
          accepted::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  private final transient PushFlowFunction pushFlowFunction_28 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          mapRef2RefFlowFunction_27,
          marketData::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  private final transient SinkPublisher quotes =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("quotes");;
  private final transient PushFlowFunction pushFlowFunction_22 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          mapRef2RefFlowFunction_21,
          quotes::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  private final transient SinkPublisher rejected =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("rejected");;
  private final transient PushFlowFunction pushFlowFunction_26 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          mapRef2RefFlowFunction_25,
          rejected::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  public final transient ServiceRegistryNode serviceRegistry = new ServiceRegistryNode();
  private final transient SinkPublisher signalOut =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("signalOut");;
  private final transient PushFlowFunction pushFlowFunction_16 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          mapRef2RefFlowFunction_15,
          signalOut::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  private final transient SinkPublisher tradeOut =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("tradeOut");;
  private final transient PushFlowFunction pushFlowFunction_30 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          mapRef2RefFlowFunction_29,
          tradeOut::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  private final transient SinkPublisher trades =
      new com.telamin.fluxtion.runtime.output.SinkPublisher<>("trades");;
  private final transient PushFlowFunction pushFlowFunction_19 =
      new com.telamin.fluxtion.runtime.flowfunction.function.PushFlowFunction<>(
          mapRef2RefFlowFunction_18,
          trades::publish,
          new com.telamin.fluxtion.runtime.partition.MethodReferenceInfo(
              "SinkPublisher->publish", false, null, false));;
  public final transient CalculatorNode calc = new CalculatorNode();
  public final transient GreeterConsumer greeterConsumer = new GreeterConsumer();
  public final transient CallbackReceiver callbackReceiver = new CallbackReceiver();
  public final transient StringConverter stringConverter = new StringConverter();
  public final transient PriceLookupNode priceLookup = new PriceLookupNode();
  public final transient TradeHandler tradeHandler = new TradeHandler();
  public final transient LoggingNode logger = new LoggingNode();
  private final transient ExportFunctionAuditEvent functionAudit = new ExportFunctionAuditEvent();
  //Dirty flags
  private boolean initCalled = false;
  private boolean processing = false;
  private boolean buffering = false;
  private final transient IdentityHashMap<Object, BooleanSupplier> dirtyFlagSupplierMap =
      new IdentityHashMap<>(26);
  private final transient IdentityHashMap<Object, Consumer<Boolean>> dirtyFlagUpdateMap =
      new IdentityHashMap<>(26);

  private boolean isDirty_filterFlowFunction_12 = false;
  private boolean isDirty_filterFlowFunction_17 = false;
  private boolean isDirty_filterFlowFunction_20 = false;
  private boolean isDirty_handlerAccepted = false;
  private boolean isDirty_handlerInteger = false;
  private boolean isDirty_handlerPriceTick = false;
  private boolean isDirty_handlerRejected = false;
  private boolean isDirty_handlerSignal_greet = false;
  private boolean isDirty_handlerStringEvent = false;
  private boolean isDirty_handlerTrade = false;
  private boolean isDirty_mapRef2RefFlowFunction_11 = false;
  private boolean isDirty_mapRef2RefFlowFunction_15 = false;
  private boolean isDirty_mapRef2RefFlowFunction_18 = false;
  private boolean isDirty_mapRef2RefFlowFunction_21 = false;
  private boolean isDirty_mapRef2RefFlowFunction_23 = false;
  private boolean isDirty_mapRef2RefFlowFunction_25 = false;
  private boolean isDirty_mapRef2RefFlowFunction_27 = false;
  private boolean isDirty_mapRef2RefFlowFunction_29 = false;
  private boolean isDirty_pushFlowFunction_13 = false;
  private boolean isDirty_pushFlowFunction_16 = false;
  private boolean isDirty_pushFlowFunction_19 = false;
  private boolean isDirty_pushFlowFunction_22 = false;
  private boolean isDirty_pushFlowFunction_24 = false;
  private boolean isDirty_pushFlowFunction_26 = false;
  private boolean isDirty_pushFlowFunction_28 = false;
  private boolean isDirty_pushFlowFunction_30 = false;

  //Forked declarations

  //Filter constants

  //unknown event handler
  private Consumer unKnownEventHandler = (e) -> {};

  public CapabilitiesProcessor(Map<Object, Object> contextMap) {
    if (context != null) {
      context.replaceMappings(contextMap);
    }
    eventLogger.setClearAfterPublish(false);
    eventLogger.trace = true;
    eventLogger.printEventToString = true;
    eventLogger.printThreadName = true;
    eventLogger.traceLevel = LogLevel.INFO;
    eventLogger.clock = clock;
    filterFlowFunction_12.setDataFlowContext(context);
    filterFlowFunction_17.setDataFlowContext(context);
    filterFlowFunction_20.setDataFlowContext(context);
    doubled.setDataFlowContext(context);
    mapRef2RefFlowFunction_11.setDataFlowContext(context);
    mapRef2RefFlowFunction_15.setDataFlowContext(context);
    mapRef2RefFlowFunction_18.setDataFlowContext(context);
    mapRef2RefFlowFunction_21.setDataFlowContext(context);
    mapRef2RefFlowFunction_23.setDataFlowContext(context);
    mapRef2RefFlowFunction_25.setDataFlowContext(context);
    mapRef2RefFlowFunction_27.setDataFlowContext(context);
    mapRef2RefFlowFunction_29.setDataFlowContext(context);
    pushFlowFunction_13.setDataFlowContext(context);
    pushFlowFunction_16.setDataFlowContext(context);
    pushFlowFunction_19.setDataFlowContext(context);
    pushFlowFunction_22.setDataFlowContext(context);
    pushFlowFunction_24.setDataFlowContext(context);
    pushFlowFunction_26.setDataFlowContext(context);
    pushFlowFunction_28.setDataFlowContext(context);
    pushFlowFunction_30.setDataFlowContext(context);
    context.setClock(clock);
    accepted.setDataFlowContext(context);
    dslOut.setDataFlowContext(context);
    marketData.setDataFlowContext(context);
    quotes.setDataFlowContext(context);
    rejected.setDataFlowContext(context);
    signalOut.setDataFlowContext(context);
    tradeOut.setDataFlowContext(context);
    trades.setDataFlowContext(context);
    serviceRegistry.setDataFlowContext(context);
    jsonDecoder.eventDispatcher = callbackDispatcher;
    numberDecoder.eventDispatcher = callbackDispatcher;
    orderRisk.eventDispatcher = callbackDispatcher;
    priceNode.eventDispatcher = callbackDispatcher;
    //node auditors
    initialiseAuditor(clock);
    initialiseAuditor(eventLogger);
    initialiseAuditor(nodeNameLookup);
    initialiseAuditor(serviceRegistry);
    if (subscriptionManager != null) {
      subscriptionManager.setSubscribingEventProcessor(this);
    }
    if (context != null) {
      context.setEventProcessorCallback(this);
    }
  }

  public CapabilitiesProcessor() {
    this(null);
  }

  @Override
  public void init() {
    initCalled = true;
    auditEvent(Lifecycle.LifecycleEvent.Init);
    //initialise dirty lookup map
    isDirty("test");
    clock.init();
    handlerAccepted.init();
    handlerInteger.init();
    doubled.initialiseEventStream();
    handlerPriceTick.init();
    handlerRejected.init();
    handlerSignal_greet.init();
    handlerStringEvent.init();
    filterFlowFunction_17.initialiseEventStream();
    filterFlowFunction_20.initialiseEventStream();
    handlerTrade.init();
    mapRef2RefFlowFunction_11.initialiseEventStream();
    filterFlowFunction_12.initialiseEventStream();
    mapRef2RefFlowFunction_15.initialiseEventStream();
    mapRef2RefFlowFunction_18.initialiseEventStream();
    mapRef2RefFlowFunction_21.initialiseEventStream();
    mapRef2RefFlowFunction_23.initialiseEventStream();
    mapRef2RefFlowFunction_25.initialiseEventStream();
    mapRef2RefFlowFunction_27.initialiseEventStream();
    mapRef2RefFlowFunction_29.initialiseEventStream();
    pushFlowFunction_13.initialiseEventStream();
    pushFlowFunction_24.initialiseEventStream();
    pushFlowFunction_28.initialiseEventStream();
    pushFlowFunction_22.initialiseEventStream();
    pushFlowFunction_26.initialiseEventStream();
    pushFlowFunction_16.initialiseEventStream();
    pushFlowFunction_30.initialiseEventStream();
    pushFlowFunction_19.initialiseEventStream();
    afterEvent();
  }

  @Override
  public void start() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before start()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Start);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void startComplete() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before startComplete()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.StartComplete);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void stop() {
    if (!initCalled) {
      throw new RuntimeException("init() must be called before stop()");
    }
    processing = true;
    auditEvent(Lifecycle.LifecycleEvent.Stop);

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void tearDown() {
    initCalled = false;
    auditEvent(Lifecycle.LifecycleEvent.TearDown);
    serviceRegistry.tearDown();
    nodeNameLookup.tearDown();
    eventLogger.tearDown();
    clock.tearDown();
    handlerTrade.tearDown();
    handlerStringEvent.tearDown();
    handlerSignal_greet.tearDown();
    handlerRejected.tearDown();
    handlerPriceTick.tearDown();
    handlerInteger.tearDown();
    handlerAccepted.tearDown();
    subscriptionManager.tearDown();
    afterEvent();
  }

  @Override
  public void setContextParameterMap(Map<Object, Object> newContextMapping) {
    context.replaceMappings(newContextMapping);
  }

  @Override
  public void addContextParameter(Object key, Object value) {
    context.addMapping(key, value);
  }

  //EVENT DISPATCH - START
  @Override
  @OnEventHandler(failBuildIfMissingBooleanReturn = false)
  public void onEvent(Object event) {
    if (buffering) {
      triggerCalculation();
    }
    if (processing) {
      callbackDispatcher.queueReentrantEvent(event);
    } else {
      processing = true;
      onEventInternal(event);
      callbackDispatcher.dispatchQueuedCallbacks();
      processing = false;
    }
  }

  @Override
  public void onEventInternal(Object event) {
    if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof Signal) {
      Signal typedEvent = (Signal) event;
      handleEvent(typedEvent);
    } else if (event instanceof SinkDeregister) {
      SinkDeregister typedEvent = (SinkDeregister) event;
      handleEvent(typedEvent);
    } else if (event instanceof SinkRegistration) {
      SinkRegistration typedEvent = (SinkRegistration) event;
      handleEvent(typedEvent);
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof ServiceEvent) {
      ServiceEvent typedEvent = (ServiceEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof StringEvent) {
      StringEvent typedEvent = (StringEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof SymbolEvent) {
      SymbolEvent typedEvent = (SymbolEvent) event;
      handleEvent(typedEvent);
    } else if (event instanceof Trade) {
      Trade typedEvent = (Trade) event;
      handleEvent(typedEvent);
    } else if (event instanceof Accepted) {
      Accepted typedEvent = (Accepted) event;
      handleEvent(typedEvent);
    } else if (event instanceof PriceTick) {
      PriceTick typedEvent = (PriceTick) event;
      handleEvent(typedEvent);
    } else if (event instanceof Rejected) {
      Rejected typedEvent = (Rejected) event;
      handleEvent(typedEvent);
    } else if (event instanceof Integer) {
      Integer typedEvent = (Integer) event;
      handleEvent(typedEvent);
    } else if (event instanceof String) {
      String typedEvent = (String) event;
      handleEvent(typedEvent);
    } else {
      unKnownEventHandler(event);
    }
  }

  public void handleEvent(EventLogControlEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(eventLogger, "eventLogger", "calculationLogConfig", typedEvent);
    eventLogger.calculationLogConfig(typedEvent);
    afterEvent();
  }

  public void handleEvent(Signal typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
      case ("greet"):
        handle_Signal_greet(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(SinkDeregister typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
      case ("accepted"):
        handle_SinkDeregister_accepted(typedEvent);
        afterEvent();
        return;
      case ("dslOut"):
        handle_SinkDeregister_dslOut(typedEvent);
        afterEvent();
        return;
      case ("marketData"):
        handle_SinkDeregister_marketData(typedEvent);
        afterEvent();
        return;
      case ("quotes"):
        handle_SinkDeregister_quotes(typedEvent);
        afterEvent();
        return;
      case ("rejected"):
        handle_SinkDeregister_rejected(typedEvent);
        afterEvent();
        return;
      case ("signalOut"):
        handle_SinkDeregister_signalOut(typedEvent);
        afterEvent();
        return;
      case ("tradeOut"):
        handle_SinkDeregister_tradeOut(typedEvent);
        afterEvent();
        return;
      case ("trades"):
        handle_SinkDeregister_trades(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(SinkRegistration typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
      case ("accepted"):
        handle_SinkRegistration_accepted(typedEvent);
        afterEvent();
        return;
      case ("dslOut"):
        handle_SinkRegistration_dslOut(typedEvent);
        afterEvent();
        return;
      case ("marketData"):
        handle_SinkRegistration_marketData(typedEvent);
        afterEvent();
        return;
      case ("quotes"):
        handle_SinkRegistration_quotes(typedEvent);
        afterEvent();
        return;
      case ("rejected"):
        handle_SinkRegistration_rejected(typedEvent);
        afterEvent();
        return;
      case ("signalOut"):
        handle_SinkRegistration_signalOut(typedEvent);
        afterEvent();
        return;
      case ("tradeOut"):
        handle_SinkRegistration_tradeOut(typedEvent);
        afterEvent();
        return;
      case ("trades"):
        handle_SinkRegistration_trades(typedEvent);
        afterEvent();
        return;
    }
    afterEvent();
  }

  public void handleEvent(ClockStrategyEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(clock, "clock", "setClockStrategy", typedEvent);
    clock.setClockStrategy(typedEvent);
    afterEvent();
  }

  public void handleEvent(ServiceEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(callbackReceiver, "callbackReceiver", "onService", typedEvent);
    callbackReceiver.onService(typedEvent);
    afterEvent();
  }

  public void handleEvent(StringEvent typedEvent) {
    auditEvent(typedEvent);
    switch (typedEvent.filterString()) {
      case ("json"):
        handle_StringEvent_json(typedEvent);
        afterEvent();
        return;
      case ("number"):
        handle_StringEvent_number(typedEvent);
        afterEvent();
        return;
      case ("order"):
        handle_StringEvent_order(typedEvent);
        afterEvent();
        return;
      case ("price"):
        handle_StringEvent_price(typedEvent);
        afterEvent();
        return;
      case ("quote"):
        handle_StringEvent_quote(typedEvent);
        afterEvent();
        return;
      case ("trade"):
        handle_StringEvent_trade(typedEvent);
        afterEvent();
        return;
    }
    //Default, no filter methods
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
    afterEvent();
  }

  public void handleEvent(SymbolEvent typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(priceLookup, "priceLookup", "onSymbol", typedEvent);
    priceLookup.onSymbol(typedEvent);
    afterEvent();
  }

  public void handleEvent(Trade typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerTrade, "handlerTrade", "onEvent", typedEvent);
    isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
    if (isDirty_handlerTrade) {
      mapRef2RefFlowFunction_29.inputUpdated(handlerTrade);
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        pushFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_pushFlowFunction_30()) {
      auditInvocation(pushFlowFunction_30, "pushFlowFunction_30", "push", typedEvent);
      isDirty_pushFlowFunction_30 = pushFlowFunction_30.push();
    }
    auditInvocation(tradeHandler, "tradeHandler", "onTrade", typedEvent);
    tradeHandler.onTrade(typedEvent);
    afterEvent();
  }

  public void handleEvent(Accepted typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerAccepted, "handlerAccepted", "onEvent", typedEvent);
    isDirty_handlerAccepted = handlerAccepted.onEvent(typedEvent);
    if (isDirty_handlerAccepted) {
      mapRef2RefFlowFunction_23.inputUpdated(handlerAccepted);
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        pushFlowFunction_24.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_pushFlowFunction_24()) {
      auditInvocation(pushFlowFunction_24, "pushFlowFunction_24", "push", typedEvent);
      isDirty_pushFlowFunction_24 = pushFlowFunction_24.push();
    }
    afterEvent();
  }

  public void handleEvent(PriceTick typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerPriceTick, "handlerPriceTick", "onEvent", typedEvent);
    isDirty_handlerPriceTick = handlerPriceTick.onEvent(typedEvent);
    if (isDirty_handlerPriceTick) {
      mapRef2RefFlowFunction_27.inputUpdated(handlerPriceTick);
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        pushFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_pushFlowFunction_28()) {
      auditInvocation(pushFlowFunction_28, "pushFlowFunction_28", "push", typedEvent);
      isDirty_pushFlowFunction_28 = pushFlowFunction_28.push();
    }
    afterEvent();
  }

  public void handleEvent(Rejected typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerRejected, "handlerRejected", "onEvent", typedEvent);
    isDirty_handlerRejected = handlerRejected.onEvent(typedEvent);
    if (isDirty_handlerRejected) {
      mapRef2RefFlowFunction_25.inputUpdated(handlerRejected);
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        pushFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_pushFlowFunction_26()) {
      auditInvocation(pushFlowFunction_26, "pushFlowFunction_26", "push", typedEvent);
      isDirty_pushFlowFunction_26 = pushFlowFunction_26.push();
    }
    afterEvent();
  }

  public void handleEvent(Integer typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(handlerInteger, "handlerInteger", "onEvent", typedEvent);
    isDirty_handlerInteger = handlerInteger.onEvent(typedEvent);
    if (isDirty_handlerInteger) {
      doubled.inputUpdated(handlerInteger);
      mapRef2RefFlowFunction_11.inputUpdated(handlerInteger);
    }
    if (guardCheck_doubled()) {
      auditInvocation(doubled, "doubled", "map", typedEvent);
      doubled.map();
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      auditInvocation(mapRef2RefFlowFunction_11, "mapRef2RefFlowFunction_11", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        filterFlowFunction_12.inputUpdated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_filterFlowFunction_12()) {
      auditInvocation(filterFlowFunction_12, "filterFlowFunction_12", "filter", typedEvent);
      isDirty_filterFlowFunction_12 = filterFlowFunction_12.filter();
      if (isDirty_filterFlowFunction_12) {
        pushFlowFunction_13.inputUpdated(filterFlowFunction_12);
      }
    }
    if (guardCheck_pushFlowFunction_13()) {
      auditInvocation(pushFlowFunction_13, "pushFlowFunction_13", "push", typedEvent);
      isDirty_pushFlowFunction_13 = pushFlowFunction_13.push();
    }
    afterEvent();
  }

  public void handleEvent(String typedEvent) {
    auditEvent(typedEvent);
    //Default, no filter methods
    auditInvocation(logger, "logger", "onString", typedEvent);
    logger.onString(typedEvent);
    afterEvent();
  }
  //EVENT DISPATCH - END

  //FILTERED DISPATCH - START
  private void handle_Signal_greet(Signal typedEvent) {
    auditInvocation(handlerSignal_greet, "handlerSignal_greet", "onEvent", typedEvent);
    isDirty_handlerSignal_greet = handlerSignal_greet.onEvent(typedEvent);
    if (isDirty_handlerSignal_greet) {
      mapRef2RefFlowFunction_15.inputUpdated(handlerSignal_greet);
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      auditInvocation(mapRef2RefFlowFunction_15, "mapRef2RefFlowFunction_15", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        pushFlowFunction_16.inputUpdated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_pushFlowFunction_16()) {
      auditInvocation(pushFlowFunction_16, "pushFlowFunction_16", "push", typedEvent);
      isDirty_pushFlowFunction_16 = pushFlowFunction_16.push();
    }
  }

  private void handle_SinkDeregister_accepted(SinkDeregister typedEvent) {
    auditInvocation(accepted, "accepted", "unregisterSink", typedEvent);
    accepted.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_dslOut(SinkDeregister typedEvent) {
    auditInvocation(dslOut, "dslOut", "unregisterSink", typedEvent);
    dslOut.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_marketData(SinkDeregister typedEvent) {
    auditInvocation(marketData, "marketData", "unregisterSink", typedEvent);
    marketData.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_quotes(SinkDeregister typedEvent) {
    auditInvocation(quotes, "quotes", "unregisterSink", typedEvent);
    quotes.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_rejected(SinkDeregister typedEvent) {
    auditInvocation(rejected, "rejected", "unregisterSink", typedEvent);
    rejected.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_signalOut(SinkDeregister typedEvent) {
    auditInvocation(signalOut, "signalOut", "unregisterSink", typedEvent);
    signalOut.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_tradeOut(SinkDeregister typedEvent) {
    auditInvocation(tradeOut, "tradeOut", "unregisterSink", typedEvent);
    tradeOut.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_trades(SinkDeregister typedEvent) {
    auditInvocation(trades, "trades", "unregisterSink", typedEvent);
    trades.unregisterSink(typedEvent);
  }

  private void handle_SinkRegistration_accepted(SinkRegistration typedEvent) {
    auditInvocation(accepted, "accepted", "sinkRegistration", typedEvent);
    accepted.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_dslOut(SinkRegistration typedEvent) {
    auditInvocation(dslOut, "dslOut", "sinkRegistration", typedEvent);
    dslOut.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_marketData(SinkRegistration typedEvent) {
    auditInvocation(marketData, "marketData", "sinkRegistration", typedEvent);
    marketData.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_quotes(SinkRegistration typedEvent) {
    auditInvocation(quotes, "quotes", "sinkRegistration", typedEvent);
    quotes.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_rejected(SinkRegistration typedEvent) {
    auditInvocation(rejected, "rejected", "sinkRegistration", typedEvent);
    rejected.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_signalOut(SinkRegistration typedEvent) {
    auditInvocation(signalOut, "signalOut", "sinkRegistration", typedEvent);
    signalOut.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_tradeOut(SinkRegistration typedEvent) {
    auditInvocation(tradeOut, "tradeOut", "sinkRegistration", typedEvent);
    tradeOut.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_trades(SinkRegistration typedEvent) {
    auditInvocation(trades, "trades", "sinkRegistration", typedEvent);
    trades.sinkRegistration(typedEvent);
  }

  private void handle_StringEvent_json(StringEvent typedEvent) {
    auditInvocation(jsonDecoder, "jsonDecoder", "onJson", typedEvent);
    jsonDecoder.onJson(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
  }

  private void handle_StringEvent_number(StringEvent typedEvent) {
    auditInvocation(numberDecoder, "numberDecoder", "onNumber", typedEvent);
    numberDecoder.onNumber(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
  }

  private void handle_StringEvent_order(StringEvent typedEvent) {
    auditInvocation(orderRisk, "orderRisk", "onOrder", typedEvent);
    orderRisk.onOrder(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
  }

  private void handle_StringEvent_price(StringEvent typedEvent) {
    auditInvocation(priceNode, "priceNode", "onPrice", typedEvent);
    priceNode.onPrice(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
  }

  private void handle_StringEvent_quote(StringEvent typedEvent) {
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
    auditInvocation(stringConverter, "stringConverter", "onQuote", typedEvent);
    stringConverter.onQuote(typedEvent);
  }

  private void handle_StringEvent_trade(StringEvent typedEvent) {
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
    auditInvocation(stringConverter, "stringConverter", "onTrade", typedEvent);
    stringConverter.onTrade(typedEvent);
  }
  //FILTERED DISPATCH - END

  //EXPORTED SERVICE FUNCTIONS - START
  @Override
  public void add(int arg0, int arg1) {
    beforeServiceCall("@Override\npublic void add(int arg0, int arg1)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(calc, "calc", "add", typedEvent);
    calc.add(arg0, arg1);
    afterServiceCall();
  }

  @Override
  public void deRegisterService(com.telamin.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "@Override\npublic void deRegisterService(com.telamin.fluxtion.runtime.service.Service<?> arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(serviceRegistry, "serviceRegistry", "deRegisterService", typedEvent);
    serviceRegistry.deRegisterService(arg0);
    afterServiceCall();
  }

  @Override
  public void registerService(com.telamin.fluxtion.runtime.service.Service<?> arg0) {
    beforeServiceCall(
        "@Override\npublic void registerService(com.telamin.fluxtion.runtime.service.Service<?> arg0)");
    ExportFunctionAuditEvent typedEvent = functionAudit;
    auditInvocation(serviceRegistry, "serviceRegistry", "registerService", typedEvent);
    serviceRegistry.registerService(arg0);
    afterServiceCall();
  }
  //EXPORTED SERVICE FUNCTIONS - END

  //EVENT BUFFERING - START
  public void bufferEvent(Object event) {
    buffering = true;
    if (event instanceof EventLogControlEvent) {
      EventLogControlEvent typedEvent = (EventLogControlEvent) event;
      auditEvent(typedEvent);
      auditInvocation(eventLogger, "eventLogger", "calculationLogConfig", typedEvent);
      eventLogger.calculationLogConfig(typedEvent);
    } else if (event instanceof Signal) {
      Signal typedEvent = (Signal) event;
      auditEvent(typedEvent);
      switch (typedEvent.filterString()) {
        case ("greet"):
          handle_Signal_greet_bufferDispatch(typedEvent);
          afterEvent();
          return;
      }
    } else if (event instanceof SinkDeregister) {
      SinkDeregister typedEvent = (SinkDeregister) event;
      auditEvent(typedEvent);
      switch (typedEvent.filterString()) {
        case ("accepted"):
          handle_SinkDeregister_accepted_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("dslOut"):
          handle_SinkDeregister_dslOut_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("marketData"):
          handle_SinkDeregister_marketData_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("quotes"):
          handle_SinkDeregister_quotes_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("rejected"):
          handle_SinkDeregister_rejected_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("signalOut"):
          handle_SinkDeregister_signalOut_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("tradeOut"):
          handle_SinkDeregister_tradeOut_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("trades"):
          handle_SinkDeregister_trades_bufferDispatch(typedEvent);
          afterEvent();
          return;
      }
    } else if (event instanceof SinkRegistration) {
      SinkRegistration typedEvent = (SinkRegistration) event;
      auditEvent(typedEvent);
      switch (typedEvent.filterString()) {
        case ("accepted"):
          handle_SinkRegistration_accepted_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("dslOut"):
          handle_SinkRegistration_dslOut_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("marketData"):
          handle_SinkRegistration_marketData_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("quotes"):
          handle_SinkRegistration_quotes_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("rejected"):
          handle_SinkRegistration_rejected_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("signalOut"):
          handle_SinkRegistration_signalOut_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("tradeOut"):
          handle_SinkRegistration_tradeOut_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("trades"):
          handle_SinkRegistration_trades_bufferDispatch(typedEvent);
          afterEvent();
          return;
      }
    } else if (event instanceof ClockStrategyEvent) {
      ClockStrategyEvent typedEvent = (ClockStrategyEvent) event;
      auditEvent(typedEvent);
      auditInvocation(clock, "clock", "setClockStrategy", typedEvent);
      clock.setClockStrategy(typedEvent);
    } else if (event instanceof ServiceEvent) {
      ServiceEvent typedEvent = (ServiceEvent) event;
      auditEvent(typedEvent);
      auditInvocation(callbackReceiver, "callbackReceiver", "onService", typedEvent);
      callbackReceiver.onService(typedEvent);
    } else if (event instanceof StringEvent) {
      StringEvent typedEvent = (StringEvent) event;
      auditEvent(typedEvent);
      switch (typedEvent.filterString()) {
        case ("json"):
          handle_StringEvent_json_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("number"):
          handle_StringEvent_number_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("order"):
          handle_StringEvent_order_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("price"):
          handle_StringEvent_price_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("quote"):
          handle_StringEvent_quote_bufferDispatch(typedEvent);
          afterEvent();
          return;
        case ("trade"):
          handle_StringEvent_trade_bufferDispatch(typedEvent);
          afterEvent();
          return;
      }
      auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
      isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
      if (isDirty_handlerStringEvent) {
        filterFlowFunction_17.inputUpdated(handlerStringEvent);
        filterFlowFunction_20.inputUpdated(handlerStringEvent);
      }
    } else if (event instanceof SymbolEvent) {
      SymbolEvent typedEvent = (SymbolEvent) event;
      auditEvent(typedEvent);
      auditInvocation(priceLookup, "priceLookup", "onSymbol", typedEvent);
      priceLookup.onSymbol(typedEvent);
    } else if (event instanceof Trade) {
      Trade typedEvent = (Trade) event;
      auditEvent(typedEvent);
      auditInvocation(handlerTrade, "handlerTrade", "onEvent", typedEvent);
      isDirty_handlerTrade = handlerTrade.onEvent(typedEvent);
      if (isDirty_handlerTrade) {
        mapRef2RefFlowFunction_29.inputUpdated(handlerTrade);
      }
      auditInvocation(tradeHandler, "tradeHandler", "onTrade", typedEvent);
      tradeHandler.onTrade(typedEvent);
    } else if (event instanceof Accepted) {
      Accepted typedEvent = (Accepted) event;
      auditEvent(typedEvent);
      auditInvocation(handlerAccepted, "handlerAccepted", "onEvent", typedEvent);
      isDirty_handlerAccepted = handlerAccepted.onEvent(typedEvent);
      if (isDirty_handlerAccepted) {
        mapRef2RefFlowFunction_23.inputUpdated(handlerAccepted);
      }
    } else if (event instanceof PriceTick) {
      PriceTick typedEvent = (PriceTick) event;
      auditEvent(typedEvent);
      auditInvocation(handlerPriceTick, "handlerPriceTick", "onEvent", typedEvent);
      isDirty_handlerPriceTick = handlerPriceTick.onEvent(typedEvent);
      if (isDirty_handlerPriceTick) {
        mapRef2RefFlowFunction_27.inputUpdated(handlerPriceTick);
      }
    } else if (event instanceof Rejected) {
      Rejected typedEvent = (Rejected) event;
      auditEvent(typedEvent);
      auditInvocation(handlerRejected, "handlerRejected", "onEvent", typedEvent);
      isDirty_handlerRejected = handlerRejected.onEvent(typedEvent);
      if (isDirty_handlerRejected) {
        mapRef2RefFlowFunction_25.inputUpdated(handlerRejected);
      }
    } else if (event instanceof Integer) {
      Integer typedEvent = (Integer) event;
      auditEvent(typedEvent);
      auditInvocation(handlerInteger, "handlerInteger", "onEvent", typedEvent);
      isDirty_handlerInteger = handlerInteger.onEvent(typedEvent);
      if (isDirty_handlerInteger) {
        doubled.inputUpdated(handlerInteger);
        mapRef2RefFlowFunction_11.inputUpdated(handlerInteger);
      }
    } else if (event instanceof String) {
      String typedEvent = (String) event;
      auditEvent(typedEvent);
      auditInvocation(logger, "logger", "onString", typedEvent);
      logger.onString(typedEvent);
    }
  }

  private void handle_Signal_greet_bufferDispatch(Signal typedEvent) {
    auditInvocation(handlerSignal_greet, "handlerSignal_greet", "onEvent", typedEvent);
    isDirty_handlerSignal_greet = handlerSignal_greet.onEvent(typedEvent);
    if (isDirty_handlerSignal_greet) {
      mapRef2RefFlowFunction_15.inputUpdated(handlerSignal_greet);
    }
  }

  private void handle_SinkDeregister_accepted_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(accepted, "accepted", "unregisterSink", typedEvent);
    accepted.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_dslOut_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(dslOut, "dslOut", "unregisterSink", typedEvent);
    dslOut.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_marketData_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(marketData, "marketData", "unregisterSink", typedEvent);
    marketData.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_quotes_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(quotes, "quotes", "unregisterSink", typedEvent);
    quotes.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_rejected_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(rejected, "rejected", "unregisterSink", typedEvent);
    rejected.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_signalOut_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(signalOut, "signalOut", "unregisterSink", typedEvent);
    signalOut.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_tradeOut_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(tradeOut, "tradeOut", "unregisterSink", typedEvent);
    tradeOut.unregisterSink(typedEvent);
  }

  private void handle_SinkDeregister_trades_bufferDispatch(SinkDeregister typedEvent) {
    auditInvocation(trades, "trades", "unregisterSink", typedEvent);
    trades.unregisterSink(typedEvent);
  }

  private void handle_SinkRegistration_accepted_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(accepted, "accepted", "sinkRegistration", typedEvent);
    accepted.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_dslOut_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(dslOut, "dslOut", "sinkRegistration", typedEvent);
    dslOut.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_marketData_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(marketData, "marketData", "sinkRegistration", typedEvent);
    marketData.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_quotes_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(quotes, "quotes", "sinkRegistration", typedEvent);
    quotes.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_rejected_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(rejected, "rejected", "sinkRegistration", typedEvent);
    rejected.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_signalOut_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(signalOut, "signalOut", "sinkRegistration", typedEvent);
    signalOut.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_tradeOut_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(tradeOut, "tradeOut", "sinkRegistration", typedEvent);
    tradeOut.sinkRegistration(typedEvent);
  }

  private void handle_SinkRegistration_trades_bufferDispatch(SinkRegistration typedEvent) {
    auditInvocation(trades, "trades", "sinkRegistration", typedEvent);
    trades.sinkRegistration(typedEvent);
  }

  private void handle_StringEvent_json_bufferDispatch(StringEvent typedEvent) {
    auditInvocation(jsonDecoder, "jsonDecoder", "onJson", typedEvent);
    jsonDecoder.onJson(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
  }

  private void handle_StringEvent_number_bufferDispatch(StringEvent typedEvent) {
    auditInvocation(numberDecoder, "numberDecoder", "onNumber", typedEvent);
    numberDecoder.onNumber(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
  }

  private void handle_StringEvent_order_bufferDispatch(StringEvent typedEvent) {
    auditInvocation(orderRisk, "orderRisk", "onOrder", typedEvent);
    orderRisk.onOrder(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
  }

  private void handle_StringEvent_price_bufferDispatch(StringEvent typedEvent) {
    auditInvocation(priceNode, "priceNode", "onPrice", typedEvent);
    priceNode.onPrice(typedEvent);
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
  }

  private void handle_StringEvent_quote_bufferDispatch(StringEvent typedEvent) {
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    auditInvocation(stringConverter, "stringConverter", "onQuote", typedEvent);
    stringConverter.onQuote(typedEvent);
  }

  private void handle_StringEvent_trade_bufferDispatch(StringEvent typedEvent) {
    auditInvocation(handlerStringEvent, "handlerStringEvent", "onEvent", typedEvent);
    isDirty_handlerStringEvent = handlerStringEvent.onEvent(typedEvent);
    if (isDirty_handlerStringEvent) {
      filterFlowFunction_17.inputUpdated(handlerStringEvent);
      filterFlowFunction_20.inputUpdated(handlerStringEvent);
    }
    auditInvocation(stringConverter, "stringConverter", "onTrade", typedEvent);
    stringConverter.onTrade(typedEvent);
  }

  public void triggerCalculation() {
    buffering = false;
    String typedEvent = "No event information - buffered dispatch";
    if (guardCheck_doubled()) {
      auditInvocation(doubled, "doubled", "map", typedEvent);
      doubled.map();
    }
    if (guardCheck_filterFlowFunction_17()) {
      auditInvocation(filterFlowFunction_17, "filterFlowFunction_17", "filter", typedEvent);
      isDirty_filterFlowFunction_17 = filterFlowFunction_17.filter();
      if (isDirty_filterFlowFunction_17) {
        mapRef2RefFlowFunction_18.inputUpdated(filterFlowFunction_17);
      }
    }
    if (guardCheck_filterFlowFunction_20()) {
      auditInvocation(filterFlowFunction_20, "filterFlowFunction_20", "filter", typedEvent);
      isDirty_filterFlowFunction_20 = filterFlowFunction_20.filter();
      if (isDirty_filterFlowFunction_20) {
        mapRef2RefFlowFunction_21.inputUpdated(filterFlowFunction_20);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_11()) {
      auditInvocation(mapRef2RefFlowFunction_11, "mapRef2RefFlowFunction_11", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_11 = mapRef2RefFlowFunction_11.map();
      if (isDirty_mapRef2RefFlowFunction_11) {
        filterFlowFunction_12.inputUpdated(mapRef2RefFlowFunction_11);
      }
    }
    if (guardCheck_filterFlowFunction_12()) {
      auditInvocation(filterFlowFunction_12, "filterFlowFunction_12", "filter", typedEvent);
      isDirty_filterFlowFunction_12 = filterFlowFunction_12.filter();
      if (isDirty_filterFlowFunction_12) {
        pushFlowFunction_13.inputUpdated(filterFlowFunction_12);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_15()) {
      auditInvocation(mapRef2RefFlowFunction_15, "mapRef2RefFlowFunction_15", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_15 = mapRef2RefFlowFunction_15.map();
      if (isDirty_mapRef2RefFlowFunction_15) {
        pushFlowFunction_16.inputUpdated(mapRef2RefFlowFunction_15);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_18()) {
      auditInvocation(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_18 = mapRef2RefFlowFunction_18.map();
      if (isDirty_mapRef2RefFlowFunction_18) {
        pushFlowFunction_19.inputUpdated(mapRef2RefFlowFunction_18);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_21()) {
      auditInvocation(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_21 = mapRef2RefFlowFunction_21.map();
      if (isDirty_mapRef2RefFlowFunction_21) {
        pushFlowFunction_22.inputUpdated(mapRef2RefFlowFunction_21);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_23()) {
      auditInvocation(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_23 = mapRef2RefFlowFunction_23.map();
      if (isDirty_mapRef2RefFlowFunction_23) {
        pushFlowFunction_24.inputUpdated(mapRef2RefFlowFunction_23);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_25()) {
      auditInvocation(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_25 = mapRef2RefFlowFunction_25.map();
      if (isDirty_mapRef2RefFlowFunction_25) {
        pushFlowFunction_26.inputUpdated(mapRef2RefFlowFunction_25);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_27()) {
      auditInvocation(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_27 = mapRef2RefFlowFunction_27.map();
      if (isDirty_mapRef2RefFlowFunction_27) {
        pushFlowFunction_28.inputUpdated(mapRef2RefFlowFunction_27);
      }
    }
    if (guardCheck_mapRef2RefFlowFunction_29()) {
      auditInvocation(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29", "map", typedEvent);
      isDirty_mapRef2RefFlowFunction_29 = mapRef2RefFlowFunction_29.map();
      if (isDirty_mapRef2RefFlowFunction_29) {
        pushFlowFunction_30.inputUpdated(mapRef2RefFlowFunction_29);
      }
    }
    if (guardCheck_pushFlowFunction_13()) {
      auditInvocation(pushFlowFunction_13, "pushFlowFunction_13", "push", typedEvent);
      isDirty_pushFlowFunction_13 = pushFlowFunction_13.push();
    }
    if (guardCheck_pushFlowFunction_16()) {
      auditInvocation(pushFlowFunction_16, "pushFlowFunction_16", "push", typedEvent);
      isDirty_pushFlowFunction_16 = pushFlowFunction_16.push();
    }
    if (guardCheck_pushFlowFunction_19()) {
      auditInvocation(pushFlowFunction_19, "pushFlowFunction_19", "push", typedEvent);
      isDirty_pushFlowFunction_19 = pushFlowFunction_19.push();
    }
    if (guardCheck_pushFlowFunction_22()) {
      auditInvocation(pushFlowFunction_22, "pushFlowFunction_22", "push", typedEvent);
      isDirty_pushFlowFunction_22 = pushFlowFunction_22.push();
    }
    if (guardCheck_pushFlowFunction_24()) {
      auditInvocation(pushFlowFunction_24, "pushFlowFunction_24", "push", typedEvent);
      isDirty_pushFlowFunction_24 = pushFlowFunction_24.push();
    }
    if (guardCheck_pushFlowFunction_26()) {
      auditInvocation(pushFlowFunction_26, "pushFlowFunction_26", "push", typedEvent);
      isDirty_pushFlowFunction_26 = pushFlowFunction_26.push();
    }
    if (guardCheck_pushFlowFunction_28()) {
      auditInvocation(pushFlowFunction_28, "pushFlowFunction_28", "push", typedEvent);
      isDirty_pushFlowFunction_28 = pushFlowFunction_28.push();
    }
    if (guardCheck_pushFlowFunction_30()) {
      auditInvocation(pushFlowFunction_30, "pushFlowFunction_30", "push", typedEvent);
      isDirty_pushFlowFunction_30 = pushFlowFunction_30.push();
    }
    afterEvent();
  }
  //EVENT BUFFERING - END

  private void auditEvent(Object typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void auditEvent(Event typedEvent) {
    clock.eventReceived(typedEvent);
    eventLogger.eventReceived(typedEvent);
    nodeNameLookup.eventReceived(typedEvent);
    serviceRegistry.eventReceived(typedEvent);
  }

  private void auditInvocation(Object node, String nodeName, String methodName, Object typedEvent) {
    eventLogger.nodeInvoked(node, nodeName, methodName, typedEvent);
  }

  private void initialiseAuditor(Auditor auditor) {
    auditor.init();
    auditor.nodeRegistered(callbackDispatcher, "callbackDispatcher");
    auditor.nodeRegistered(filterFlowFunction_12, "filterFlowFunction_12");
    auditor.nodeRegistered(filterFlowFunction_17, "filterFlowFunction_17");
    auditor.nodeRegistered(filterFlowFunction_20, "filterFlowFunction_20");
    auditor.nodeRegistered(doubled, "doubled");
    auditor.nodeRegistered(mapRef2RefFlowFunction_11, "mapRef2RefFlowFunction_11");
    auditor.nodeRegistered(mapRef2RefFlowFunction_15, "mapRef2RefFlowFunction_15");
    auditor.nodeRegistered(mapRef2RefFlowFunction_18, "mapRef2RefFlowFunction_18");
    auditor.nodeRegistered(mapRef2RefFlowFunction_21, "mapRef2RefFlowFunction_21");
    auditor.nodeRegistered(mapRef2RefFlowFunction_23, "mapRef2RefFlowFunction_23");
    auditor.nodeRegistered(mapRef2RefFlowFunction_25, "mapRef2RefFlowFunction_25");
    auditor.nodeRegistered(mapRef2RefFlowFunction_27, "mapRef2RefFlowFunction_27");
    auditor.nodeRegistered(mapRef2RefFlowFunction_29, "mapRef2RefFlowFunction_29");
    auditor.nodeRegistered(pushFlowFunction_13, "pushFlowFunction_13");
    auditor.nodeRegistered(pushFlowFunction_16, "pushFlowFunction_16");
    auditor.nodeRegistered(pushFlowFunction_19, "pushFlowFunction_19");
    auditor.nodeRegistered(pushFlowFunction_22, "pushFlowFunction_22");
    auditor.nodeRegistered(pushFlowFunction_24, "pushFlowFunction_24");
    auditor.nodeRegistered(pushFlowFunction_26, "pushFlowFunction_26");
    auditor.nodeRegistered(pushFlowFunction_28, "pushFlowFunction_28");
    auditor.nodeRegistered(pushFlowFunction_30, "pushFlowFunction_30");
    auditor.nodeRegistered(subscriptionManager, "subscriptionManager");
    auditor.nodeRegistered(handlerAccepted, "handlerAccepted");
    auditor.nodeRegistered(handlerInteger, "handlerInteger");
    auditor.nodeRegistered(handlerPriceTick, "handlerPriceTick");
    auditor.nodeRegistered(handlerRejected, "handlerRejected");
    auditor.nodeRegistered(handlerSignal_greet, "handlerSignal_greet");
    auditor.nodeRegistered(handlerStringEvent, "handlerStringEvent");
    auditor.nodeRegistered(handlerTrade, "handlerTrade");
    auditor.nodeRegistered(context, "context");
    auditor.nodeRegistered(accepted, "accepted");
    auditor.nodeRegistered(dslOut, "dslOut");
    auditor.nodeRegistered(marketData, "marketData");
    auditor.nodeRegistered(quotes, "quotes");
    auditor.nodeRegistered(rejected, "rejected");
    auditor.nodeRegistered(signalOut, "signalOut");
    auditor.nodeRegistered(tradeOut, "tradeOut");
    auditor.nodeRegistered(trades, "trades");
    auditor.nodeRegistered(calc, "calc");
    auditor.nodeRegistered(callbackReceiver, "callbackReceiver");
    auditor.nodeRegistered(greeterConsumer, "greeterConsumer");
    auditor.nodeRegistered(jsonDecoder, "jsonDecoder");
    auditor.nodeRegistered(logger, "logger");
    auditor.nodeRegistered(numberDecoder, "numberDecoder");
    auditor.nodeRegistered(priceLookup, "priceLookup");
    auditor.nodeRegistered(stringConverter, "stringConverter");
    auditor.nodeRegistered(tradeHandler, "tradeHandler");
    auditor.nodeRegistered(orderRisk, "orderRisk");
    auditor.nodeRegistered(priceNode, "priceNode");
  }

  private void beforeServiceCall(String functionDescription) {
    functionAudit.setFunctionDescription(functionDescription);
    auditEvent(functionAudit);
    if (buffering) {
      triggerCalculation();
    }
    processing = true;
  }

  private void afterServiceCall() {
    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  private void afterEvent() {
    clock.processingComplete();
    eventLogger.processingComplete();
    nodeNameLookup.processingComplete();
    serviceRegistry.processingComplete();
    isDirty_filterFlowFunction_12 = false;
    isDirty_filterFlowFunction_17 = false;
    isDirty_filterFlowFunction_20 = false;
    isDirty_handlerAccepted = false;
    isDirty_handlerInteger = false;
    isDirty_handlerPriceTick = false;
    isDirty_handlerRejected = false;
    isDirty_handlerSignal_greet = false;
    isDirty_handlerStringEvent = false;
    isDirty_handlerTrade = false;
    isDirty_mapRef2RefFlowFunction_11 = false;
    isDirty_mapRef2RefFlowFunction_15 = false;
    isDirty_mapRef2RefFlowFunction_18 = false;
    isDirty_mapRef2RefFlowFunction_21 = false;
    isDirty_mapRef2RefFlowFunction_23 = false;
    isDirty_mapRef2RefFlowFunction_25 = false;
    isDirty_mapRef2RefFlowFunction_27 = false;
    isDirty_mapRef2RefFlowFunction_29 = false;
    isDirty_pushFlowFunction_13 = false;
    isDirty_pushFlowFunction_16 = false;
    isDirty_pushFlowFunction_19 = false;
    isDirty_pushFlowFunction_22 = false;
    isDirty_pushFlowFunction_24 = false;
    isDirty_pushFlowFunction_26 = false;
    isDirty_pushFlowFunction_28 = false;
    isDirty_pushFlowFunction_30 = false;
  }

  @Override
  public void batchPause() {
    auditEvent(Lifecycle.LifecycleEvent.BatchPause);
    processing = true;

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public void batchEnd() {
    auditEvent(Lifecycle.LifecycleEvent.BatchEnd);
    processing = true;

    afterEvent();
    callbackDispatcher.dispatchQueuedCallbacks();
    processing = false;
  }

  @Override
  public boolean isDirty(Object node) {
    return dirtySupplier(node).getAsBoolean();
  }

  @Override
  public BooleanSupplier dirtySupplier(Object node) {
    if (dirtyFlagSupplierMap.isEmpty()) {
      dirtyFlagSupplierMap.put(filterFlowFunction_12, () -> isDirty_filterFlowFunction_12);
      dirtyFlagSupplierMap.put(filterFlowFunction_17, () -> isDirty_filterFlowFunction_17);
      dirtyFlagSupplierMap.put(filterFlowFunction_20, () -> isDirty_filterFlowFunction_20);
      dirtyFlagSupplierMap.put(handlerAccepted, () -> isDirty_handlerAccepted);
      dirtyFlagSupplierMap.put(handlerInteger, () -> isDirty_handlerInteger);
      dirtyFlagSupplierMap.put(handlerPriceTick, () -> isDirty_handlerPriceTick);
      dirtyFlagSupplierMap.put(handlerRejected, () -> isDirty_handlerRejected);
      dirtyFlagSupplierMap.put(handlerSignal_greet, () -> isDirty_handlerSignal_greet);
      dirtyFlagSupplierMap.put(handlerStringEvent, () -> isDirty_handlerStringEvent);
      dirtyFlagSupplierMap.put(handlerTrade, () -> isDirty_handlerTrade);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_11, () -> isDirty_mapRef2RefFlowFunction_11);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_15, () -> isDirty_mapRef2RefFlowFunction_15);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_18, () -> isDirty_mapRef2RefFlowFunction_18);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_21, () -> isDirty_mapRef2RefFlowFunction_21);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_23, () -> isDirty_mapRef2RefFlowFunction_23);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_25, () -> isDirty_mapRef2RefFlowFunction_25);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_27, () -> isDirty_mapRef2RefFlowFunction_27);
      dirtyFlagSupplierMap.put(mapRef2RefFlowFunction_29, () -> isDirty_mapRef2RefFlowFunction_29);
      dirtyFlagSupplierMap.put(pushFlowFunction_13, () -> isDirty_pushFlowFunction_13);
      dirtyFlagSupplierMap.put(pushFlowFunction_16, () -> isDirty_pushFlowFunction_16);
      dirtyFlagSupplierMap.put(pushFlowFunction_19, () -> isDirty_pushFlowFunction_19);
      dirtyFlagSupplierMap.put(pushFlowFunction_22, () -> isDirty_pushFlowFunction_22);
      dirtyFlagSupplierMap.put(pushFlowFunction_24, () -> isDirty_pushFlowFunction_24);
      dirtyFlagSupplierMap.put(pushFlowFunction_26, () -> isDirty_pushFlowFunction_26);
      dirtyFlagSupplierMap.put(pushFlowFunction_28, () -> isDirty_pushFlowFunction_28);
      dirtyFlagSupplierMap.put(pushFlowFunction_30, () -> isDirty_pushFlowFunction_30);
    }
    return dirtyFlagSupplierMap.getOrDefault(node, DataFlow.ALWAYS_FALSE);
  }

  @Override
  public void setDirty(Object node, boolean dirtyFlag) {
    if (dirtyFlagUpdateMap.isEmpty()) {
      dirtyFlagUpdateMap.put(filterFlowFunction_12, (b) -> isDirty_filterFlowFunction_12 = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_17, (b) -> isDirty_filterFlowFunction_17 = b);
      dirtyFlagUpdateMap.put(filterFlowFunction_20, (b) -> isDirty_filterFlowFunction_20 = b);
      dirtyFlagUpdateMap.put(handlerAccepted, (b) -> isDirty_handlerAccepted = b);
      dirtyFlagUpdateMap.put(handlerInteger, (b) -> isDirty_handlerInteger = b);
      dirtyFlagUpdateMap.put(handlerPriceTick, (b) -> isDirty_handlerPriceTick = b);
      dirtyFlagUpdateMap.put(handlerRejected, (b) -> isDirty_handlerRejected = b);
      dirtyFlagUpdateMap.put(handlerSignal_greet, (b) -> isDirty_handlerSignal_greet = b);
      dirtyFlagUpdateMap.put(handlerStringEvent, (b) -> isDirty_handlerStringEvent = b);
      dirtyFlagUpdateMap.put(handlerTrade, (b) -> isDirty_handlerTrade = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_11, (b) -> isDirty_mapRef2RefFlowFunction_11 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_15, (b) -> isDirty_mapRef2RefFlowFunction_15 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_18, (b) -> isDirty_mapRef2RefFlowFunction_18 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_21, (b) -> isDirty_mapRef2RefFlowFunction_21 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_23, (b) -> isDirty_mapRef2RefFlowFunction_23 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_25, (b) -> isDirty_mapRef2RefFlowFunction_25 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_27, (b) -> isDirty_mapRef2RefFlowFunction_27 = b);
      dirtyFlagUpdateMap.put(
          mapRef2RefFlowFunction_29, (b) -> isDirty_mapRef2RefFlowFunction_29 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_13, (b) -> isDirty_pushFlowFunction_13 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_16, (b) -> isDirty_pushFlowFunction_16 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_19, (b) -> isDirty_pushFlowFunction_19 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_22, (b) -> isDirty_pushFlowFunction_22 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_24, (b) -> isDirty_pushFlowFunction_24 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_26, (b) -> isDirty_pushFlowFunction_26 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_28, (b) -> isDirty_pushFlowFunction_28 = b);
      dirtyFlagUpdateMap.put(pushFlowFunction_30, (b) -> isDirty_pushFlowFunction_30 = b);
    }
    dirtyFlagUpdateMap.get(node).accept(dirtyFlag);
  }

  private boolean guardCheck_filterFlowFunction_12() {
    return isDirty_mapRef2RefFlowFunction_11;
  }

  private boolean guardCheck_filterFlowFunction_17() {
    return isDirty_handlerStringEvent;
  }

  private boolean guardCheck_filterFlowFunction_20() {
    return isDirty_handlerStringEvent;
  }

  private boolean guardCheck_doubled() {
    return isDirty_handlerInteger;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_11() {
    return isDirty_handlerInteger;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_15() {
    return isDirty_handlerSignal_greet;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_18() {
    return isDirty_filterFlowFunction_17;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_21() {
    return isDirty_filterFlowFunction_20;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_23() {
    return isDirty_handlerAccepted;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_25() {
    return isDirty_handlerRejected;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_27() {
    return isDirty_handlerPriceTick;
  }

  private boolean guardCheck_mapRef2RefFlowFunction_29() {
    return isDirty_handlerTrade;
  }

  private boolean guardCheck_pushFlowFunction_13() {
    return isDirty_filterFlowFunction_12;
  }

  private boolean guardCheck_pushFlowFunction_16() {
    return isDirty_mapRef2RefFlowFunction_15;
  }

  private boolean guardCheck_pushFlowFunction_19() {
    return isDirty_mapRef2RefFlowFunction_18;
  }

  private boolean guardCheck_pushFlowFunction_22() {
    return isDirty_mapRef2RefFlowFunction_21;
  }

  private boolean guardCheck_pushFlowFunction_24() {
    return isDirty_mapRef2RefFlowFunction_23;
  }

  private boolean guardCheck_pushFlowFunction_26() {
    return isDirty_mapRef2RefFlowFunction_25;
  }

  private boolean guardCheck_pushFlowFunction_28() {
    return isDirty_mapRef2RefFlowFunction_27;
  }

  private boolean guardCheck_pushFlowFunction_30() {
    return isDirty_mapRef2RefFlowFunction_29;
  }

  private boolean guardCheck_accepted() {
    return isDirty_pushFlowFunction_24;
  }

  private boolean guardCheck_dslOut() {
    return isDirty_pushFlowFunction_13;
  }

  private boolean guardCheck_marketData() {
    return isDirty_pushFlowFunction_28;
  }

  private boolean guardCheck_quotes() {
    return isDirty_pushFlowFunction_22;
  }

  private boolean guardCheck_rejected() {
    return isDirty_pushFlowFunction_26;
  }

  private boolean guardCheck_signalOut() {
    return isDirty_pushFlowFunction_16;
  }

  private boolean guardCheck_tradeOut() {
    return isDirty_pushFlowFunction_30;
  }

  private boolean guardCheck_trades() {
    return isDirty_pushFlowFunction_19;
  }

  @Override
  public <T> T getNodeById(String id) throws NoSuchFieldException {
    try {
      return nodeNameLookup.getInstanceById(id);
    } catch (NoSuchFieldException miss) {
      // Auditors live on the SEP as public fields rather than in
      // nodeNameLookup. Fall back to a reflective field probe so
      // callers (especially DataFlow.getServiceById) have a single
      // unified lookup path — no need to know whether the id maps to
      // a regular node or an auditor.
      try {
        @SuppressWarnings("unchecked")
        T t = (T) this.getClass().getField(id).get(this);
        return t;
      } catch (IllegalAccessException unreachable) {
        throw new NoSuchFieldException(id);
      } catch (NoSuchFieldException stillMissing) {
        throw miss;
      }
    }
  }

  @Override
  public <A extends Auditor> A getAuditorById(String id)
      throws NoSuchFieldException, IllegalAccessException {
    return (A) this.getClass().getField(id).get(this);
  }

  @Override
  public void addEventFeed(EventFeed eventProcessorFeed) {
    subscriptionManager.addEventProcessorFeed(eventProcessorFeed);
  }

  @Override
  public void removeEventFeed(EventFeed eventProcessorFeed) {
    subscriptionManager.removeEventProcessorFeed(eventProcessorFeed);
  }

  @Override
  public CapabilitiesProcessor newInstance() {
    return new CapabilitiesProcessor();
  }

  @Override
  public CapabilitiesProcessor newInstance(Map<Object, Object> contextMap) {
    return new CapabilitiesProcessor();
  }

  @Override
  public String getLastAuditLogRecord() {
    try {
      EventLogManager eventLogManager =
          (EventLogManager) this.getClass().getField(EventLogManager.NODE_NAME).get(this);
      return eventLogManager.lastRecordAsString();
    } catch (Throwable e) {
      return "";
    }
  }

  public void unKnownEventHandler(Object object) {
    unKnownEventHandler.accept(object);
  }

  @Override
  public <T> void setUnKnownEventHandler(Consumer<T> consumer) {
    unKnownEventHandler = consumer;
  }

  @Override
  public SubscriptionManager getSubscriptionManager() {
    return subscriptionManager;
  }
}
