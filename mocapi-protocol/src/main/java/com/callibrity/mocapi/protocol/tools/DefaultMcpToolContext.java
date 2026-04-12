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

import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.LoggingMessageNotificationParams;
import com.callibrity.mocapi.model.ProgressNotificationParams;
import com.callibrity.mocapi.protocol.McpTransport;
import com.callibrity.mocapi.protocol.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ValueNode;

/**
 * Default {@link McpToolContext} implementation that sends notifications through an {@link
 * McpTransport} and captures the final result for the dispatch pipeline.
 */
public class DefaultMcpToolContext<R> implements McpToolContext<R> {

  private final McpTransport transport;
  private final ObjectMapper objectMapper;
  private final ValueNode progressToken;

  private R result;
  private boolean resultSent;

  public DefaultMcpToolContext(
      McpTransport transport, ObjectMapper objectMapper, ValueNode progressToken) {
    this.transport = transport;
    this.objectMapper = objectMapper;
    this.progressToken = progressToken;
  }

  @Override
  public void sendProgress(long progress, long total) {
    if (progressToken == null) {
      return;
    }
    var params = new ProgressNotificationParams(progressToken, progress, (double) total, null);
    transport.send(
        new JsonRpcNotification("2.0", "notifications/progress", objectMapper.valueToTree(params)));
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
        new JsonRpcNotification("2.0", "notifications/message", objectMapper.valueToTree(params)));
  }

  @Override
  public void sendResult(R result) {
    if (resultSent) {
      throw new IllegalStateException("Result already sent");
    }
    this.result = result;
    this.resultSent = true;
  }

  public R getResult() {
    return result;
  }

  public boolean isResultSent() {
    return resultSent;
  }
}
