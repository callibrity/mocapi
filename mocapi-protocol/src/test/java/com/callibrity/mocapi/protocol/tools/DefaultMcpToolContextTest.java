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
package com.callibrity.mocapi.protocol.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.protocol.CapturingTransport;
import com.callibrity.mocapi.protocol.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class DefaultMcpToolContextTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void sendProgressSendsNotificationThroughTransport() {
    var transport = new CapturingTransport();
    var token = JsonNodeFactory.instance.textNode("progress-1");
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, token);

    ctx.sendProgress(5, 10);

    assertThat(transport.messages()).hasSize(1);
    var msg = (JsonRpcNotification) transport.messages().getFirst();
    assertThat(msg.method()).isEqualTo("notifications/progress");
    assertThat(msg.params().get("progressToken").asString()).isEqualTo("progress-1");
    assertThat(msg.params().get("progress").asDouble()).isEqualTo(5.0);
    assertThat(msg.params().get("total").asDouble()).isEqualTo(10.0);
  }

  @Test
  void sendProgressWithNullTokenIsNoOp() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);

    ctx.sendProgress(5, 10);

    assertThat(transport.messages()).isEmpty();
  }

  @Test
  void logSendsNotificationThroughTransport() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);

    ctx.log(LoggingLevel.INFO, "test-logger", "hello");

    assertThat(transport.messages()).hasSize(1);
    var msg = (JsonRpcNotification) transport.messages().getFirst();
    assertThat(msg.method()).isEqualTo("notifications/message");
    assertThat(msg.params().get("level").asString()).isEqualTo("info");
    assertThat(msg.params().get("logger").asString()).isEqualTo("test-logger");
    assertThat(msg.params().get("data").asString()).isEqualTo("hello");
  }

  @Test
  void logBelowSessionLevelIsDropped() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);
    var session = new McpSession("2025-11-25", null, null, LoggingLevel.WARNING, "s1");

    ScopedValue.where(McpSession.CURRENT, session)
        .run(() -> ctx.log(LoggingLevel.DEBUG, "test", "dropped"));

    assertThat(transport.messages()).isEmpty();
  }

  @Test
  void logAtOrAboveSessionLevelIsSent() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);
    var session = new McpSession("2025-11-25", null, null, LoggingLevel.WARNING, "s1");

    ScopedValue.where(McpSession.CURRENT, session)
        .run(() -> ctx.log(LoggingLevel.ERROR, "test", "sent"));

    assertThat(transport.messages()).hasSize(1);
  }

  @Test
  void sendResultCapturesResult() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);

    ctx.sendResult("done");

    assertThat(ctx.isResultSent()).isTrue();
    assertThat(ctx.getResult()).isEqualTo("done");
  }

  @Test
  void sendResultTwiceThrowsIllegalState() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);

    ctx.sendResult("first");

    assertThatThrownBy(() -> ctx.sendResult("second")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void elicitThrowsUnsupportedOperation() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);

    assertThatThrownBy(() -> ctx.elicit("message"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void sampleThrowsUnsupportedOperation() {
    var transport = new CapturingTransport();
    var ctx = new DefaultMcpToolContext<String>(transport, mapper, null);

    assertThatThrownBy(() -> ctx.sample("prompt", 100))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
