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

import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.ProgressNotificationParams;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.elicitation.ElicitationDispatcher;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ValueNode;

/**
 * Default {@link McpToolContext} implementation. Progress notifications flow through the
 * request-scoped {@link McpTransport}; elicitation routes through the {@link ElicitationDispatcher}
 * seam after a per-request capability check against the {@link McpExchange} (a client that did not
 * declare the {@code elicitation} capability gets {@link McpElicitationNotSupportedException},
 * surfaced on the wire as the spec's {@code MissingRequiredClientCapabilityError}).
 */
public class DefaultMcpToolContext implements McpToolContext {

  private final McpTransport transport;
  private final ObjectMapper objectMapper;
  private final ValueNode progressToken;
  private final ElicitationDispatcher elicitationDispatcher;
  private final McpExchange exchange;
  private final String handlerName;

  public DefaultMcpToolContext(
      McpTransport transport,
      ObjectMapper objectMapper,
      ValueNode progressToken,
      ElicitationDispatcher elicitationDispatcher,
      McpExchange exchange,
      String handlerName) {
    this.transport = transport;
    this.objectMapper = objectMapper;
    this.progressToken = progressToken;
    this.elicitationDispatcher = elicitationDispatcher;
    this.exchange = exchange;
    this.handlerName = handlerName;
  }

  @Override
  public String handlerName() {
    return handlerName;
  }

  @Override
  public void sendProgress(long progress, long total) {
    if (progressToken == null) {
      return;
    }
    var params = new ProgressNotificationParams(progressToken, progress, (double) total, null);
    transport.send(
        new JsonRpcNotification(
            "2.0", McpMethods.NOTIFICATIONS_PROGRESS, objectMapper.valueToTree(params)));
  }

  @Override
  public ElicitResult elicit(ElicitRequestFormParams params) {
    if (exchange == null || !exchange.supportsElicitationForm()) {
      throw new McpElicitationNotSupportedException(
          "Client did not declare the elicitation capability in clientCapabilities");
    }
    return elicitationDispatcher.elicit(params);
  }
}
