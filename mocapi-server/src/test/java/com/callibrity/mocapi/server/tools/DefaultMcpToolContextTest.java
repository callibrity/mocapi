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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class DefaultMcpToolContextTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Mock private McpResponseCorrelationService correlationService;

  @Test
  void sendProgressSendsNotificationThroughTransport() {
    var transport = mock(McpTransport.class);
    var token = JsonNodeFactory.instance.textNode("progress-1");
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
  void sendProgressWithNullTokenIsNoOp() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);

    ctx.sendProgress(5, 10);

    verifyNoInteractions(transport);
  }

  @Test
  void logSendsNotificationThroughTransport() {
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
  void logBelowSessionLevelIsDropped() {
    var transport = mock(McpTransport.class);
    var ctx = new DefaultMcpToolContext(transport, mapper, null, correlationService);
    var session = new McpSession("s1", "2025-11-25", null, null, LoggingLevel.WARNING);

    ScopedValue.where(McpSession.CURRENT, session)
        .run(() -> ctx.log(LoggingLevel.DEBUG, "test", "dropped"));

    verifyNoInteractions(transport);
  }

  @Test
  void logAtOrAboveSessionLevelIsSent() {
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
  void elicitDelegatesToCorrelationService() throws Exception {
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
        .sendAndAwait(
            eq(McpMethods.ELICITATION_CREATE),
            eq(requestParams),
            eq(ElicitResult.class),
            eq(transport));
  }

  @Test
  void sampleDelegatesToCorrelationService() throws Exception {
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
            eq(McpMethods.SAMPLING_CREATE_MESSAGE),
            eq(requestParams),
            eq(CreateMessageResult.class),
            eq(transport));
  }
}
