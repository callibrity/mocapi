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
  private final Number total;
  private boolean started;
  private double last;

  ProgressChannel(McpTransport transport, ValueNode progressToken, Number total) {
    this.transport = transport;
    this.progressToken = progressToken;
    this.total = total;
  }

  void emit(Number progress, String message) {
    double value = progress.doubleValue();
    if (started && value <= last) {
      throw new IllegalArgumentException(
          String.format(
              "progress must strictly increase with each notification: previous=%s, got=%s",
              last, value));
    }
    started = true;
    last = value;
    if (progressToken == null || transport == null) {
      return;
    }
    ObjectNode params = JsonNodeFactory.instance.objectNode();
    params.set("progressToken", progressToken);
    putNumber(params, "progress", progress);
    if (total != null) {
      putNumber(params, "total", total);
    }
    if (message != null) {
      params.put("message", message);
    }
    transport.send(new JsonRpcNotification("2.0", McpMethods.NOTIFICATIONS_PROGRESS, params));
  }

  /**
   * Writes a numeric field preserving the emitter's chosen wire type: integral emitters (long,
   * counting) serialize whole-number JSON ({@code 5}), floating emitters (double, percent)
   * serialize floating-point JSON ({@code 5.0}). Both are valid per the spec's {@code number} type.
   */
  private static void putNumber(ObjectNode node, String field, Number value) {
    if (value instanceof Double || value instanceof Float) {
      node.put(field, value.doubleValue());
    } else {
      node.put(field, value.longValue());
    }
  }
}
