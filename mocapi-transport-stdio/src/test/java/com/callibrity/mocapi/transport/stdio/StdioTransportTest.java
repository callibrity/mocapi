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

import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class StdioTransportTest {

  private ByteArrayOutputStream buffer;
  private PrintStream out;
  private AtomicReference<String> sessionIdCapture;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private StdioTransport transport;

  @BeforeEach
  void setUp() {
    buffer = new ByteArrayOutputStream();
    out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    sessionIdCapture = new AtomicReference<>();
    transport = new StdioTransport(objectMapper, out, sessionIdCapture::set);
  }

  @Test
  void sendResultWritesNewlineDelimitedJsonToStdout() {
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("k", "v"),
            JsonNodeFactory.instance.numberNode(1));

    transport.send(result);

    String output = buffer.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .isEqualTo(
            "{\"jsonrpc\":\"2.0\",\"result\":{\"k\":\"v\"},\"id\":1}" + System.lineSeparator());
  }

  @Test
  void sendNotificationWritesNewlineDelimitedJsonToStdout() {
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    transport.send(notification);

    String output = buffer.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .startsWith("{")
        .contains("\"jsonrpc\":\"2.0\"")
        .contains("\"method\":\"notifications/progress\"")
        .endsWith(System.lineSeparator());
  }

  @Test
  void sendMultipleMessagesProducesOneLinePerMessage() {
    transport.send(
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1)));
    transport.send(
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(2)));

    String[] lines = buffer.toString(StandardCharsets.UTF_8).split(System.lineSeparator());
    assertThat(lines).hasSize(2);
    assertThat(lines[0]).contains("\"id\":1");
    assertThat(lines[1]).contains("\"id\":2");
  }

  @Test
  void emitSessionInitializedDeliversSessionIdToSink() {
    transport.emit(new McpEvent.SessionInitialized("session-abc", "2025-11-25"));

    assertThat(sessionIdCapture.get()).isEqualTo("session-abc");
  }
}
