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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.elicitation.ElicitationDispatcher;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
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

  @Mock private ElicitationDispatcher elicitationDispatcher;

  private static McpExchange exchange(ClientCapabilities capabilities) {
    return new McpExchange(
        McpServer.PROTOCOL_VERSION,
        new Implementation("test-client", null, "1.0", null),
        capabilities);
  }

  private static McpExchange formCapableExchange() {
    return exchange(
        new ClientCapabilities(null, null, null, new ElicitationCapability(null, null), null));
  }

  @Nested
  class Progress {

    @Test
    void send_progress_sends_notification_through_transport() {
      var transport = mock(McpTransport.class);
      var token = JsonNodeFactory.instance.stringNode("progress-1");
      var ctx =
          new DefaultMcpToolContext(
              transport, mapper, token, elicitationDispatcher, formCapableExchange(), "tool");

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
      var ctx =
          new DefaultMcpToolContext(
              transport, mapper, null, elicitationDispatcher, formCapableExchange(), "tool");

      ctx.sendProgress(5, 10);

      verifyNoInteractions(transport);
    }
  }

  @Nested
  class Elicitation {

    private final ElicitRequestFormParams requestParams =
        new ElicitRequestFormParams("Please provide info", null);

    @Test
    void elicit_routes_to_dispatcher_when_client_is_form_capable() {
      var ctx =
          new DefaultMcpToolContext(
              mock(McpTransport.class),
              mapper,
              null,
              elicitationDispatcher,
              formCapableExchange(),
              "tool");
      var expectedResult = new ElicitResult(ElicitAction.ACCEPT, mapper.createObjectNode());
      when(elicitationDispatcher.elicit(requestParams)).thenReturn(expectedResult);

      var result = ctx.elicit(requestParams);

      assertThat(result).isSameAs(expectedResult);
      verify(elicitationDispatcher).elicit(requestParams);
    }

    @Test
    void elicit_throws_not_supported_when_capability_is_absent() {
      var ctx =
          new DefaultMcpToolContext(
              mock(McpTransport.class),
              mapper,
              null,
              elicitationDispatcher,
              exchange(new ClientCapabilities(null, null, null, null, null)),
              "tool");

      assertThatThrownBy(() -> ctx.elicit(requestParams))
          .isInstanceOf(McpElicitationNotSupportedException.class)
          .hasMessageContaining("elicitation");
      verifyNoInteractions(elicitationDispatcher);
    }

    @Test
    void elicit_throws_not_supported_when_exchange_is_absent() {
      var ctx =
          new DefaultMcpToolContext(
              mock(McpTransport.class), mapper, null, elicitationDispatcher, null, "tool");

      assertThatThrownBy(() -> ctx.elicit(requestParams))
          .isInstanceOf(McpElicitationNotSupportedException.class);
      verifyNoInteractions(elicitationDispatcher);
    }

    @Test
    void bare_elicitation_capability_counts_as_form_support() {
      var ctx =
          new DefaultMcpToolContext(
              mock(McpTransport.class),
              mapper,
              null,
              elicitationDispatcher,
              formCapableExchange(),
              "tool");
      when(elicitationDispatcher.elicit(requestParams))
          .thenReturn(new ElicitResult(ElicitAction.DECLINE, null));

      assertThat(ctx.elicit(requestParams).action()).isEqualTo(ElicitAction.DECLINE);
    }
  }

  @Test
  void handler_name_is_reported() {
    var ctx =
        new DefaultMcpToolContext(
            mock(McpTransport.class),
            mapper,
            null,
            elicitationDispatcher,
            formCapableExchange(),
            "my-tool");

    assertThat(ctx.handlerName()).isEqualTo("my-tool");
  }
}
