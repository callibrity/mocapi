/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.server.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.server.DefaultMcpServer;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.McpTransportResolver;
import com.callibrity.mocapi.server.elicitation.ElicitationNotSupportedExceptionTranslator;
import com.callibrity.mocapi.server.exchange.MetaEnvelopeParser;
import com.callibrity.mocapi.server.lifecycle.McpLifecycleService;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethodHandler;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethodHandlerCustomizer;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethodHandlers;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcDispatcher;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcExceptionTranslator;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcExceptionTranslatorRegistry;
import com.callibrity.ripcurl.core.def.IllegalArgumentExceptionTranslator;
import com.callibrity.ripcurl.core.def.ParameterResolutionExceptionTranslator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Shared test infrastructure for MCP compliance tests, stateless-model edition (ADR-0020): no
 * sessions, no handshake — every call carries the {@code _meta} envelope.
 */
final class ComplianceTestSupport {

  static final String PROTOCOL_VERSION = McpServer.PROTOCOL_VERSION;
  static final ObjectMapper MAPPER = new ObjectMapper();

  /** Fixed MRTR secret so requestState tokens round-trip across separately built servers. */
  static final String MRTR_SECRET =
      Base64.getEncoder().encodeToString("compliance-test-secret-32-bytes!".getBytes());

  private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

  private ComplianceTestSupport() {}

  // --- Dispatcher ---

  static JsonRpcDispatcher buildDispatcher(Object... services) {
    // Ripcurl always puts JsonRpcParamsResolver at the head of the chain and the
    // Jackson3ParameterResolver at the tail; mocapi's transport resolver slots in between
    // via the customizer SPI.
    List<JsonRpcMethodHandlerCustomizer> customizers =
        List.of(config -> config.resolver(new McpTransportResolver()));
    List<JsonRpcMethodHandler> handlers = new ArrayList<>();
    for (Object service : services) {
      for (var method :
          MethodUtils.getMethodsListWithAnnotation(service.getClass(), JsonRpcMethod.class)) {
        handlers.add(JsonRpcMethodHandlers.build(service, method, MAPPER, customizers));
      }
    }
    return new DefaultJsonRpcDispatcher(
        handlers,
        new DefaultJsonRpcExceptionTranslatorRegistry(
            List.of(
                new DefaultJsonRpcExceptionTranslator(),
                new IllegalArgumentExceptionTranslator(),
                new ParameterResolutionExceptionTranslator(),
                new ElicitationNotSupportedExceptionTranslator(MAPPER))));
  }

  // --- MRTR ---

  /** An MRTR engine with the shared test secret and the default TTL. */
  static MrtrElicitationEngine mrtrEngine() {
    return mrtrEngine(RequestStateCodec.DEFAULT_TTL);
  }

  /** An MRTR engine with the shared test secret and the given TTL. */
  static MrtrElicitationEngine mrtrEngine(Duration ttl) {
    return new MrtrElicitationEngine(
        RequestStateCodec.withSecret(MRTR_SECRET, ttl, MAPPER), MAPPER);
  }

  // --- Server ---

  static McpServer buildServer(Object... services) {
    Object[] allServices = new Object[services.length + 1];
    allServices[0] = new McpLifecycleService();
    System.arraycopy(services, 0, allServices, 1, services.length);
    var dispatcher = buildDispatcher(allServices);
    return new DefaultMcpServer(dispatcher, new MetaEnvelopeParser(MAPPER), MAPPER);
  }

  // --- Envelope builders ---

  /** A valid {@code _meta} envelope for the given protocol version, no client capabilities. */
  static ObjectNode envelope(String protocolVersion) {
    ObjectNode meta = JsonNodeFactory.instance.objectNode();
    meta.put(McpMetaKeys.PROTOCOL_VERSION, protocolVersion);
    ObjectNode clientInfo = meta.putObject(McpMetaKeys.CLIENT_INFO);
    clientInfo.put("name", "test-client");
    clientInfo.put("version", "1.0");
    meta.putObject(McpMetaKeys.CLIENT_CAPABILITIES);
    return meta;
  }

  /** A valid envelope for the supported protocol version. */
  static ObjectNode envelope() {
    return envelope(PROTOCOL_VERSION);
  }

  /** A valid envelope whose clientCapabilities declares form elicitation support. */
  static ObjectNode envelopeWithElicitation() {
    ObjectNode meta = envelope();
    ((ObjectNode) meta.get(McpMetaKeys.CLIENT_CAPABILITIES)).putObject("elicitation");
    return meta;
  }

  // --- Call builders ---

  /** A call whose params carry only the {@code _meta} envelope. */
  static JsonRpcCall call(String method) {
    ObjectNode params = JsonNodeFactory.instance.objectNode();
    params.set("_meta", envelope());
    return JsonRpcCall.of(method, params, nextId());
  }

  /** A call whose params are {@code params} plus the {@code _meta} envelope. */
  static JsonRpcCall call(String method, Object params) {
    return callWithMeta(method, params, envelope());
  }

  /** A call whose params are {@code params} plus the given {@code _meta} envelope. */
  static JsonRpcCall callWithMeta(String method, Object params, ObjectNode meta) {
    ObjectNode paramsNode = (ObjectNode) MAPPER.valueToTree(params);
    paramsNode.set("_meta", meta);
    return JsonRpcCall.of(method, paramsNode, nextId());
  }

  /** A call with NO {@code _meta} envelope — invalid on every method. */
  static JsonRpcCall callWithoutEnvelope(String method, Object params) {
    JsonNode paramsNode = params == null ? null : MAPPER.valueToTree(params);
    return JsonRpcCall.of(method, paramsNode, nextId());
  }

  static JsonRpcNotification notification(String method) {
    return JsonRpcNotification.of(method, null);
  }

  static JsonRpcNotification notification(String method, Object params) {
    return JsonRpcNotification.of(method, MAPPER.valueToTree(params));
  }

  // --- Capture helpers ---

  static JsonRpcMessage captureMessage(McpTransport transport) {
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    return captor.getValue();
  }

  static JsonRpcResult captureResult(McpTransport transport) {
    var msg = captureMessage(transport);
    assertThat(msg).isInstanceOf(JsonRpcResult.class);
    return (JsonRpcResult) msg;
  }

  static JsonRpcError captureError(McpTransport transport) {
    var msg = captureMessage(transport);
    assertThat(msg).isInstanceOf(JsonRpcError.class);
    return (JsonRpcError) msg;
  }

  // --- ID generation ---

  private static JsonNode nextId() {
    return JsonNodeFactory.instance.numberNode(ID_COUNTER.getAndIncrement());
  }
}
