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
package com.callibrity.mocapi.transport.stdio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpContextResult;
import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class StdioServerTest {

  @Mock McpServer server;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ByteArrayOutputStream buffer;
  private PrintStream out;
  private AtomicReference<String> sessionIdHolder;
  private StdioTransport transport;

  @BeforeEach
  void setUp() {
    buffer = new ByteArrayOutputStream();
    out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    sessionIdHolder = new AtomicReference<>();
    transport = new StdioTransport(objectMapper, out, sessionIdHolder::set);
  }

  @Nested
  class InitializeCall {

    @Test
    void dispatchesWithEmptyContext() throws Exception {
      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1,\"params\":{}}");

      verify(server, timeout(2000))
          .handleCall(eq(McpContext.empty()), any(JsonRpcCall.class), any());
    }

    @Test
    void handlerCanSetSessionIdViaTransportEmit() throws Exception {
      doAnswer(
              inv -> {
                McpTransport t = inv.getArgument(2);
                t.emit(new McpEvent.SessionInitialized("new-sess", "2025-11-25"));
                t.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(server)
          .handleCall(any(), any(), any());

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1,\"params\":{}}");

      verify(server, timeout(2000)).handleCall(any(), any(), any());
      Thread.sleep(100);
      assertThat(sessionIdHolder.get()).isEqualTo("new-sess");
    }
  }

  @Nested
  class PostInitializeCalls {

    @BeforeEach
    void sessionAlreadyInitialized() {
      sessionIdHolder.set("session-1");
    }

    @Test
    void dispatchesWithValidContext() throws Exception {
      McpContext ctx = validContext("session-1");
      when(server.createContext("session-1", null))
          .thenReturn(new McpContextResult.ValidContext(ctx));

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1}");

      verify(server, timeout(2000)).handleCall(eq(ctx), any(JsonRpcCall.class), any());
    }

    @Test
    void notificationDispatchesWithValidContext() throws Exception {
      when(server.createContext("session-1", null))
          .thenReturn(new McpContextResult.ValidContext(validContext("session-1")));

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

      verify(server, timeout(2000)).handleNotification(any(), any(JsonRpcNotification.class));
    }

    @Test
    void clientResponseRoutesToCorrelation() throws Exception {
      when(server.createContext("session-1", null))
          .thenReturn(new McpContextResult.ValidContext(validContext("session-1")));

      runLines("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\",\"result\":{}}");

      verify(server, timeout(2000)).handleResponse(any(), any());
    }

    @Test
    void handlerExceptionProducesJsonRpcError() throws Exception {
      when(server.createContext("session-1", null))
          .thenReturn(new McpContextResult.ValidContext(validContext("session-1")));
      doAnswer(
              inv -> {
                throw new RuntimeException("boom");
              })
          .when(server)
          .handleCall(any(), any(), any());

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":42}");

      verify(server, timeout(2000)).handleCall(any(), any(JsonRpcCall.class), any());
      Thread.sleep(200);
      assertThat(buffer.toString(StandardCharsets.UTF_8))
          .contains("\"error\"")
          .contains("Internal error")
          .contains("boom");
    }
  }

  @Nested
  class ContextFailures {

    @Test
    void sessionIdRequiredProducesJsonRpcError() throws Exception {
      when(server.createContext(any(), any()))
          .thenReturn(new McpContextResult.SessionIdRequired(-32000, "session required"));

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1}");

      verify(server, timeout(2000)).createContext(any(), any());
      verify(server, never()).handleCall(any(), any(), any());
      assertThat(buffer.toString(StandardCharsets.UTF_8))
          .contains("\"error\"")
          .contains("session required");
    }

    @Test
    void sessionNotFoundProducesJsonRpcError() throws Exception {
      sessionIdHolder.set("gone");
      when(server.createContext(anyString(), any()))
          .thenReturn(new McpContextResult.SessionNotFound(-32001, "Session not found"));

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1}");

      verify(server, timeout(2000)).createContext(any(), any());
      Thread.sleep(200);
      assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("Session not found");
    }

    @Test
    void protocolVersionMismatchProducesJsonRpcError() throws Exception {
      sessionIdHolder.set("session-1");
      when(server.createContext(anyString(), any()))
          .thenReturn(new McpContextResult.ProtocolVersionMismatch(-32002, "mismatch"));

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1}");

      verify(server, timeout(2000)).createContext(any(), any());
      Thread.sleep(200);
      assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("mismatch");
    }

    @Test
    void errorResponseEchoesCallId() throws Exception {
      sessionIdHolder.set("gone");
      when(server.createContext(anyString(), any()))
          .thenReturn(new McpContextResult.SessionNotFound(-32001, "Session not found"));

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":77}");

      verify(server, timeout(2000)).createContext(any(), any());
      Thread.sleep(200);
      JsonRpcError parsed =
          objectMapper.readValue(
              buffer.toString(StandardCharsets.UTF_8).strip(), JsonRpcError.class);
      assertThat(parsed.id().asInt()).isEqualTo(77);
    }
  }

  @Nested
  class PreInitialize {

    @Test
    void notificationIsDroppedSilently() throws Exception {
      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

      Thread.sleep(200);
      verify(server, never()).handleNotification(any(), any());
      verify(server, never()).createContext(any(), any());
    }

    @Test
    void clientResponseIsDroppedSilently() throws Exception {
      runLines("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\",\"result\":{}}");

      Thread.sleep(200);
      verify(server, never()).handleResponse(any(), any());
      verify(server, never()).createContext(any(), any());
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void malformedJsonLineIsDroppedAndReaderContinues() throws Exception {
      sessionIdHolder.set("session-1");
      when(server.createContext("session-1", null))
          .thenReturn(new McpContextResult.ValidContext(validContext("session-1")));

      runLines("this is not json\n" + "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1}");

      verify(server, timeout(2000)).handleCall(any(), any(JsonRpcCall.class), any());
    }

    @Test
    void eofCausesRunToReturn() throws Exception {
      runLines("");

      verify(server, never()).handleCall(any(), any(), any());
      verify(server, never()).handleNotification(any(), any());
    }
  }

  // --- helpers ---

  private void runLines(String input) throws Exception {
    var reader = new StringReader(input.isEmpty() ? "" : input + "\n");
    var stdio = new StdioServer(server, objectMapper, transport, reader, sessionIdHolder::get);
    stdio.run();
  }

  private McpContext validContext(String sessionId) {
    return new StubMcpContext(sessionId);
  }

  private record StubMcpContext(String sessionId) implements McpContext {
    @Override
    public String protocolVersion() {
      return "2025-11-25";
    }

    @Override
    public Optional<McpSession> session() {
      return Optional.empty();
    }
  }
}
