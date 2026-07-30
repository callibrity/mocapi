/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StdioServerTest {

  private static final String ENVELOPE =
      "\"_meta\":{\"%s\":\"%s\",\"%s\":{\"name\":\"test-client\",\"version\":\"1.0\"},\"%s\":{}}"
          .formatted(
              McpMetaKeys.PROTOCOL_VERSION,
              McpServer.PROTOCOL_VERSION,
              McpMetaKeys.CLIENT_INFO,
              McpMetaKeys.CLIENT_CAPABILITIES);

  @Mock McpServer server;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ByteArrayOutputStream buffer;
  private StdioTransport transport;

  @BeforeEach
  void set_up() {
    buffer = new ByteArrayOutputStream();
    transport =
        new StdioTransport(objectMapper, new PrintStream(buffer, true, StandardCharsets.UTF_8));
  }

  // --- helpers ---

  private void runLines(String input) throws IOException {
    var reader = new BufferedReader(new StringReader(input.isEmpty() ? "" : input + "\n"));
    new StdioServer(server, objectMapper, transport, reader).run();
  }

  private String stdout() {
    return buffer.toString(StandardCharsets.UTF_8);
  }

  @Nested
  class Stateless_dispatch {

    @Test
    void discover_is_answerable_as_the_first_message() throws Exception {
      // No handshake gating: server/discover (the back-compat probe) dispatches immediately.
      runLines(
          "{\"jsonrpc\":\"2.0\",\"method\":\"server/discover\",\"id\":1,\"params\":{"
              + ENVELOPE
              + "}}");

      verify(server, timeout(2000))
          .handleCall(
              argThat((JsonRpcCall call) -> "server/discover".equals(call.method())),
              eq(transport));
    }

    @Test
    void tool_call_with_envelope_dispatches_to_the_server() throws Exception {
      runLines(
          "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"id\":2,\"params\":{\"name\":\"echo\","
              + ENVELOPE
              + "}}");

      verify(server, timeout(2000))
          .handleCall(
              argThat(
                  (JsonRpcCall call) ->
                      "tools/call".equals(call.method())
                          && call.params().get("_meta").has(McpMetaKeys.PROTOCOL_VERSION)),
              eq(transport));
    }

    @Test
    void server_response_is_relayed_to_stdout() throws Exception {
      doAnswer(
              inv -> {
                McpTransport t = inv.getArgument(1);
                t.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode().put("ok", true),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(server)
          .handleCall(any(), any());

      runLines(
          "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1,\"params\":{" + ENVELOPE + "}}");

      Awaitility.await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(() -> assertThat(stdout()).contains("\"ok\":true"));
    }

    @Test
    void missing_envelope_error_from_the_server_is_relayed() throws Exception {
      // Envelope semantics are the server core's job; the transport just relays its -32602.
      doAnswer(
              inv -> {
                JsonRpcCall call = inv.getArgument(0);
                McpTransport t = inv.getArgument(1);
                t.send(call.error(-32602, "Missing required _meta envelope on request params"));
                return null;
              })
          .when(server)
          .handleCall(any(), any());

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":7}");

      Awaitility.await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(
              () ->
                  assertThat(stdout())
                      .contains("-32602")
                      .contains("Missing required _meta envelope")
                      .contains("\"id\":7"));
    }
  }

  @Nested
  class Notifications {

    @Test
    void notification_dispatches_without_any_gating() throws Exception {
      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\"}");

      verify(server, timeout(2000))
          .handleNotification(
              argThat((JsonRpcNotification n) -> "notifications/cancelled".equals(n.method())));
      assertThat(stdout()).isEmpty();
    }

    @Test
    void notification_handler_exception_is_swallowed_and_logged() throws Exception {
      doAnswer(
              inv -> {
                throw new RuntimeException("boom");
              })
          .when(server)
          .handleNotification(any());

      runLines("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\"}");

      verify(server, timeout(2000)).handleNotification(any());
      assertThat(stdout()).isEmpty();
    }
  }

  @Nested
  class Client_responses {

    @Test
    void client_response_is_dropped_without_dispatch() throws Exception {
      // No server-initiated requests in 2026-07-28 — nothing to correlate a response to.
      runLines("{\"jsonrpc\":\"2.0\",\"id\":\"req-1\",\"result\":{}}");

      verify(server, never()).handleCall(any(), any());
      verify(server, never()).handleNotification(any());
      assertThat(stdout()).isEmpty();
    }
  }

  @Nested
  class Handler_failures {

    @Test
    void handler_exception_produces_internal_error_with_the_call_id() throws Exception {
      doAnswer(
              inv -> {
                throw new RuntimeException("boom");
              })
          .when(server)
          .handleCall(any(), any());

      runLines(
          "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":42,\"params\":{"
              + ENVELOPE
              + "}}");

      verify(server, timeout(2000)).handleCall(any(JsonRpcCall.class), any());
      Awaitility.await()
          .atMost(Duration.ofSeconds(2))
          .untilAsserted(
              () ->
                  assertThat(stdout())
                      .contains("\"error\"")
                      .contains("-32603")
                      .contains("Internal error")
                      .contains("boom")
                      .contains("\"id\":42"));
    }
  }

  @Nested
  class Reader_lifecycle {

    @Test
    void malformed_json_line_is_dropped_and_reader_continues() throws Exception {
      runLines(
          "this is not json\n{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":1,\"params\":{"
              + ENVELOPE
              + "}}");

      verify(server, timeout(2000)).handleCall(any(JsonRpcCall.class), any());
    }

    @Test
    void eof_causes_run_to_return_cleanly() throws Exception {
      runLines("");

      verify(server, never()).handleCall(any(), any());
      verify(server, never()).handleNotification(any());
    }
  }
}
