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

import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.LoggingMessageNotificationParams;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.ProgressNotificationParams;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ValueNode;

/**
 * Default {@link McpToolContext} implementation that delegates to an {@link McpTransport} for
 * mid-execution communication (progress, logging, elicitation, sampling).
 */
public class DefaultMcpToolContext implements McpToolContext {

  private final McpTransport transport;
  private final ObjectMapper objectMapper;
  private final ValueNode progressToken;
  private final McpResponseCorrelationService correlationService;

  public DefaultMcpToolContext(
      McpTransport transport,
      ObjectMapper objectMapper,
      ValueNode progressToken,
      McpResponseCorrelationService correlationService) {
    this.transport = transport;
    this.objectMapper = objectMapper;
    this.progressToken = progressToken;
    this.correlationService = correlationService;
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
  public void log(LoggingLevel level, String logger, String message) {
    if (McpSession.CURRENT.isBound()) {
      McpSession session = McpSession.CURRENT.get();
      if (level.ordinal() < session.logLevel().ordinal()) {
        return;
      }
    }
    var params = new LoggingMessageNotificationParams(level, logger, message, null);
    transport.send(
        new JsonRpcNotification(
            "2.0", McpMethods.NOTIFICATIONS_MESSAGE, objectMapper.valueToTree(params)));
  }

  @Override
  public ElicitResult elicit(ElicitRequestFormParams params) {
    return correlationService.sendAndAwait(
        McpMethods.ELICITATION_CREATE, params, ElicitResult.class, transport);
  }

  @Override
  public CreateMessageResult sample(CreateMessageRequestParams params) {
    return correlationService.sendAndAwait(
        McpMethods.SAMPLING_CREATE_MESSAGE, params, CreateMessageResult.class, transport);
  }
}
