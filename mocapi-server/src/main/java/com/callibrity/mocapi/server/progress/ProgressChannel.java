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
package com.callibrity.mocapi.server.progress;

import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ValueNode;

/**
 * The single-operation engine behind every progress emitter (ADR-0025). Tracks the last reported
 * value to enforce the spec's strictly-increasing contract, and — when a progress token is present
 * — emits a {@code notifications/progress} notification per accepted update.
 *
 * <p>The wire params mirror {@code ProgressNotificationParams}; the node is built directly (no
 * {@code ObjectMapper}) so prompt/resource dispatch need not carry one. Validation runs regardless
 * of the token; the token (and a bound transport) gate only the actual send, so a non-increasing
 * bug surfaces the same way whether or not the client asked for progress.
 */
class ProgressChannel {

  private final McpTransport transport;
  private final ValueNode progressToken;
  private final Double total;
  private boolean started;
  private double last;

  ProgressChannel(McpTransport transport, ValueNode progressToken, Double total) {
    this.transport = transport;
    this.progressToken = progressToken;
    this.total = total;
  }

  void emit(double progress, String message) {
    if (started && progress <= last) {
      throw new IllegalArgumentException(
          String.format(
              "progress must strictly increase with each notification: previous=%s, got=%s",
              last, progress));
    }
    started = true;
    last = progress;
    if (progressToken == null || transport == null) {
      return;
    }
    ObjectNode params = JsonNodeFactory.instance.objectNode();
    params.set("progressToken", progressToken);
    params.put("progress", progress);
    if (total != null) {
      params.put("total", total.doubleValue());
    }
    if (message != null) {
      params.put("message", message);
    }
    transport.send(new JsonRpcNotification("2.0", McpMethods.NOTIFICATIONS_PROGRESS, params));
  }
}
