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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpContextResult;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.transport.http.sse.SseStreamFactory;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcResult;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationView;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ContextPropagationTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";

  @Mock private McpServer server;
  @Mock private SseStreamFactory sseStreamFactory;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final McpRequestValidator validator = new McpRequestValidator(List.of("localhost"));

  @Test
  void stub_thread_local_accessor_propagates_across_vt_spawn() throws Exception {
    ThreadLocal<String> tl = new ThreadLocal<>();
    ContextRegistry registry = new ContextRegistry().registerThreadLocalAccessor("stub.tl", tl);
    ContextSnapshotFactory factory =
        ContextSnapshotFactory.builder().contextRegistry(registry).build();
    StreamableHttpController controller = newController(factory);

    when(server.createContext(anyString(), any())).thenReturn(validContext("s1"));
    AtomicReference<String> observedInsideHandler = new AtomicReference<>();
    doAnswer(
            invocation -> {
              observedInsideHandler.set(tl.get());
              McpTransport transport = invocation.getArgument(2);
              transport.send(okResult());
              return null;
            })
        .when(server)
        .handleCall(any(), any(), any());

    tl.set("request-thread-value");
    try {
      invokeHandlerOnVt(controller).get(5, TimeUnit.SECONDS);
    } finally {
      tl.remove();
    }

    assertThat(observedInsideHandler.get()).isEqualTo("request-thread-value");

    // Cleared-after check: verify ContextSnapshot.wrap clears the ThreadLocal when the
    // wrapped runnable exits, on a thread that had no prior value (the VT case).
    tl.set("seed");
    var snapshot = factory.captureAll();
    tl.remove();
    AtomicReference<String> duringWrap = new AtomicReference<>();
    snapshot.wrap(() -> duringWrap.set(tl.get())).run();
    assertThat(duringWrap.get()).isEqualTo("seed");
    assertThat(tl.get()).isNull();
  }

  @Test
  void security_context_propagates_to_handler_vt() throws Exception {
    ContextSnapshotFactory factory = ContextSnapshotFactory.builder().build();
    StreamableHttpController controller = newController(factory);

    when(server.createContext(anyString(), any())).thenReturn(validContext("s1"));
    AtomicReference<SecurityContext> captured = new AtomicReference<>();
    doAnswer(
            invocation -> {
              captured.set(SecurityContextHolder.getContext());
              McpTransport transport = invocation.getArgument(2);
              transport.send(okResult());
              return null;
            })
        .when(server)
        .handleCall(any(), any(), any());

    var auth = new TestingAuthenticationToken("alice", "credentials");
    auth.setAuthenticated(true);
    SecurityContext sc = new SecurityContextImpl(auth);
    SecurityContextHolder.setContext(sc);
    try {
      invokeHandlerOnVt(controller).get(5, TimeUnit.SECONDS);
    } finally {
      SecurityContextHolder.clearContext();
    }

    assertThat(captured.get()).isNotNull();
    assertThat(captured.get().getAuthentication().getName()).isEqualTo("alice");
  }

  @Test
  void observation_started_inside_handler_sees_outer_observation_as_parent() throws Exception {
    ObservationRegistry observationRegistry = ObservationRegistry.create();
    observationRegistry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<Observation.Context>() {
              @Override
              public boolean supportsContext(Observation.Context context) {
                return true;
              }
            });
    ContextSnapshotFactory factory = ContextSnapshotFactory.builder().build();
    StreamableHttpController controller = newController(factory);

    when(server.createContext(anyString(), any())).thenReturn(validContext("s1"));
    AtomicReference<ObservationView> childParent = new AtomicReference<>();
    doAnswer(
            invocation -> {
              Observation child = Observation.createNotStarted("mcp.tool", observationRegistry);
              childParent.set(child.getContextView().getParentObservation());
              McpTransport transport = invocation.getArgument(2);
              transport.send(okResult());
              return null;
            })
        .when(server)
        .handleCall(any(), any(), any());

    Observation outer = Observation.start("http.request", observationRegistry);
    try (var _ = outer.openScope()) {
      invokeHandlerOnVt(controller).get(5, TimeUnit.SECONDS);
    } finally {
      outer.stop();
    }

    assertThat(childParent.get()).isSameAs(outer);
    // Ensure the accessor is the one ObservationThreadLocalAccessor uses, not an
    // unrelated ThreadLocal accident.
    assertThat(ObservationThreadLocalAccessor.KEY).isEqualTo("micrometer.observation");
  }

  private StreamableHttpController newController(ContextSnapshotFactory factory) {
    return new StreamableHttpController(server, validator, sseStreamFactory, objectMapper, factory);
  }

  private CompletableFuture<ResponseEntity<Object>> invokeHandlerOnVt(
      StreamableHttpController controller) {
    return controller.handlePost(
        objectMapper.treeToValue(callRequest(), JsonRpcMessage.class),
        null,
        "s1",
        POST_ACCEPT,
        null);
  }

  private static JsonRpcResult okResult() {
    return new JsonRpcResult(
        JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));
  }

  private ObjectNode callRequest() {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", "ping");
    request.put("id", 1);
    return request;
  }

  private static McpContextResult validContext(String sessionId) {
    return new McpContextResult.ValidContext(new StubMcpContext(sessionId));
  }

  private record StubMcpContext(String sessionId) implements McpContext {
    @Override
    public String protocolVersion() {
      return "2025-11-25";
    }

    @Override
    public Optional<com.callibrity.mocapi.server.session.McpSession> session() {
      return Optional.empty();
    }
  }
}
