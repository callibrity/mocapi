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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMcpToolContextTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Mock private McpResponseCorrelationService correlationService;

  @Test
  void send_progress_sends_notification_through_transport() {
    var transport = mock(McpTransport.class);
    var token = JsonNodeFactory.instance.stringNode("progress-1");
    var ctx = new DefaultMcpToolContext(transport, mapper, token, correlationService);

    ctx.sendProgress(5, 10);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    var msg = (JsonRpcNotification) captor.getValue();
    assertThat(msg.method()).isEqualTo("notifications/progress");
    assertThat(msg.params().get("progressToken").asString()).isEqualTo("progress-1");
    assertThat(msg.params().get("progress").asDouble()).isEqualTo(5.0);
    assertThat(msg.params().get("total").asDouble()).isEqualTo(10.0);
  }

  @Test
  void send_progress_with_null_token_is_no_op() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);

    ctx.sendProgress(5, 10);

    verifyNoInteractions(transport);
  }

  @Test
  void log_sends_notification_through_transport() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);

    ctx.log(LoggingLevel.INFO, "test-logger", "hello");

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    var msg = (JsonRpcNotification) captor.getValue();
    assertThat(msg.method()).isEqualTo("notifications/message");
    assertThat(msg.params().get("level").asString()).isEqualTo("info");
    assertThat(msg.params().get("logger").asString()).isEqualTo("test-logger");
    assertThat(msg.params().get("data").asString()).isEqualTo("hello");
  }

  @Test
  void log_below_session_level_is_dropped() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);
    var session = new McpSession("s1", "2025-11-25", null, null, LoggingLevel.WARNING);

    ScopedValue.where(McpSession.CURRENT, session)
        .run(() -> ctx.log(LoggingLevel.DEBUG, "test", "dropped"));

    verifyNoInteractions(transport);
  }

  @Test
  void log_at_or_above_session_level_is_sent() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);
    var session = new McpSession("s1", "2025-11-25", null, null, LoggingLevel.WARNING);

    ScopedValue.where(McpSession.CURRENT, session)
        .run(() -> ctx.log(LoggingLevel.ERROR, "test", "sent"));

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(JsonRpcNotification.class);
  }

  @Test
  void elicit_delegates_to_correlation_service() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);
    var requestParams =
        new ElicitRequestFormParams("form", "Please provide info", null, null, null);
    var expectedResult = new ElicitResult(ElicitAction.ACCEPT, mapper.createObjectNode());
    when(correlationService.sendAndAwait(
            eq(McpMethods.ELICITATION_CREATE),
            eq(requestParams),
            eq(ElicitResult.class),
            any(McpTransport.class)))
        .thenReturn(expectedResult);

    var result = ctx.elicit(requestParams);

    assertThat(result).isSameAs(expectedResult);
    verify(correlationService)
        .sendAndAwait(McpMethods.ELICITATION_CREATE, requestParams, ElicitResult.class, transport);
  }

  @Test
  void sample_delegates_to_correlation_service() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);
    var requestParams =
        new CreateMessageRequestParams(
            List.of(), null, null, null, null, 100, null, null, null, null, null, null);
    var expectedResult =
        new CreateMessageResult(Role.ASSISTANT, new TextContent("Hello", null), "model-1", "end");
    when(correlationService.sendAndAwait(
            eq(McpMethods.SAMPLING_CREATE_MESSAGE),
            eq(requestParams),
            eq(CreateMessageResult.class),
            any(McpTransport.class)))
        .thenReturn(expectedResult);

    var result = ctx.sample(requestParams);

    assertThat(result).isSameAs(expectedResult);
    verify(correlationService)
        .sendAndAwait(
            McpMethods.SAMPLING_CREATE_MESSAGE,
            requestParams,
            CreateMessageResult.class,
            transport);
  }

  @Test
  void handler_name_defaults_to_mcp() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);

    assertThat(ctx.handlerName()).isEqualTo("mcp");
  }

  @Test
  void handler_name_from_constructor_is_returned() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService, null, "greet");

    assertThat(ctx.handlerName()).isEqualTo("greet");
  }

  @Test
  void logger_routes_through_log_with_handler_name() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService, null, "greet");

    ctx.logger().info("hi {}", "there");

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    var msg = (JsonRpcNotification) captor.getValue();
    assertThat(msg.method()).isEqualTo("notifications/message");
    assertThat(msg.params().get("level").asString()).isEqualTo("info");
    assertThat(msg.params().get("logger").asString()).isEqualTo("greet");
    assertThat(msg.params().get("data").asString()).isEqualTo("hi there");
  }

  @Test
  void is_enabled_below_session_level_returns_false() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);
    var session = new McpSession("s1", "2025-11-25", null, null, LoggingLevel.WARNING);

    ScopedValue.where(McpSession.CURRENT, session)
        .run(
            () -> {
              assertThat(ctx.isEnabled(LoggingLevel.DEBUG)).isFalse();
              assertThat(ctx.isEnabled(LoggingLevel.WARNING)).isTrue();
              assertThat(ctx.isEnabled(LoggingLevel.ERROR)).isTrue();
            });
  }

  @Test
  void is_enabled_without_session_permits_everything() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);

    assertThat(ctx.isEnabled(LoggingLevel.DEBUG)).isTrue();
    assertThat(ctx.isEnabled(LoggingLevel.EMERGENCY)).isTrue();
  }
}
