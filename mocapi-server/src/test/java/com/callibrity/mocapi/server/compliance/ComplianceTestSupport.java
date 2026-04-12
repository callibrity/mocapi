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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.DefaultMcpServer;
import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.ServerCapabilitiesContributor;
import com.callibrity.mocapi.server.autoconfigure.SessionStoreTestSupport;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import com.callibrity.ripcurl.core.annotation.DefaultAnnotationJsonRpcMethodProviderFactory;
import com.callibrity.ripcurl.core.annotation.JsonRpcParamsResolver;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcDispatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/** Shared test infrastructure for MCP compliance tests. */
final class ComplianceTestSupport {

  static final String PROTOCOL_VERSION = InitializeResult.PROTOCOL_VERSION;
  static final ObjectMapper MAPPER = new ObjectMapper();

  private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

  private ComplianceTestSupport() {}

  // --- Dispatcher ---

  static JsonRpcDispatcher buildDispatcher(ObjectMapper mapper, Object... services) {
    var invokerFactory =
        new DefaultMethodInvokerFactory(List.of(new JsonRpcParamsResolver(mapper)));
    var factory = new DefaultAnnotationJsonRpcMethodProviderFactory(mapper, invokerFactory);
    var providers = Arrays.stream(services).map(factory::create).toList();
    return new DefaultJsonRpcDispatcher(providers);
  }

  // --- Session service ---

  static McpSessionService buildSessionService(
      McpSessionStore store, ServerCapabilities capabilities) {
    return new McpSessionService(
        store,
        Duration.ofHours(1),
        new Implementation("test-server", "Test Server", "1.0.0"),
        null,
        capabilitiesContributors(capabilities));
  }

  // --- Server ---

  static McpServer buildServer(
      McpSessionService sessionService,
      McpResponseCorrelationService correlationService,
      Object... services) {
    Object[] allServices = new Object[services.length + 1];
    allServices[0] = sessionService;
    System.arraycopy(services, 0, allServices, 1, services.length);
    var dispatcher = buildDispatcher(MAPPER, allServices);
    return new DefaultMcpServer(sessionService, dispatcher, correlationService);
  }

  static McpServer buildServer(
      McpSessionStore store,
      ServerCapabilities capabilities,
      McpResponseCorrelationService correlationService,
      Object... services) {
    return buildServer(buildSessionService(store, capabilities), correlationService, services);
  }

  static McpServer buildServer(
      McpSessionStore store, ServerCapabilities capabilities, Object... services) {
    return buildServer(store, capabilities, mock(McpResponseCorrelationService.class), services);
  }

  // --- Context ---

  static McpContext noSession() {
    return new SimpleContext(null, null);
  }

  static McpContext withSession(String sessionId) {
    return new SimpleContext(sessionId, PROTOCOL_VERSION);
  }

  record SimpleContext(String sessionId, String protocolVersion) implements McpContext {}

  // --- Call builders ---

  static JsonRpcCall initializeCall() {
    var params =
        MAPPER.valueToTree(
            Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "test-client", "version", "1.0")));
    return JsonRpcCall.of("initialize", params, nextId());
  }

  static JsonRpcCall initializeCallWithCapabilities(Map<String, Object> capabilities) {
    var params =
        MAPPER.valueToTree(
            Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", capabilities,
                "clientInfo", Map.of("name", "test-client", "version", "1.0")));
    return JsonRpcCall.of("initialize", params, nextId());
  }

  static JsonRpcCall call(String method) {
    return JsonRpcCall.of(method, null, nextId());
  }

  static JsonRpcCall call(String method, Object params) {
    return JsonRpcCall.of(method, MAPPER.valueToTree(params), nextId());
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

  static McpEvent captureEvent(McpTransport transport) {
    var captor = ArgumentCaptor.forClass(McpEvent.class);
    verify(transport).emit(captor.capture());
    return captor.getValue();
  }

  // --- Session helpers ---

  static String initializeAndGetSessionId(McpServer server) {
    var transport = mock(McpTransport.class);
    server.handleCall(noSession(), initializeCall(), transport);
    return ((McpEvent.SessionInitialized) captureEvent(transport)).sessionId();
  }

  // --- Session store (real implementation with in-memory substrate) ---

  static McpSessionStore inMemorySessionStore() {
    return SessionStoreTestSupport.create();
  }

  // --- Capabilities contributors ---

  private static List<ServerCapabilitiesContributor> capabilitiesContributors(
      ServerCapabilities capabilities) {
    return List.of(
        builder -> {
          if (capabilities.tools() != null) builder.tools(capabilities.tools());
          if (capabilities.resources() != null) builder.resources(capabilities.resources());
          if (capabilities.prompts() != null) builder.prompts(capabilities.prompts());
          if (capabilities.logging() != null) builder.logging(capabilities.logging());
          if (capabilities.completions() != null) builder.completions(capabilities.completions());
        });
  }

  // --- ID generation ---

  private static tools.jackson.databind.JsonNode nextId() {
    return JsonNodeFactory.instance.numberNode(ID_COUNTER.getAndIncrement());
  }
}
