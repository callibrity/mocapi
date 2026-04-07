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
package com.callibrity.mocapi.autoconfigure.sse;

import org.jwcarman.odyssey.core.OdysseyStream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Handle that MCP handler methods can declare as a parameter to opt into SSE streaming. Provides
 * methods for sending intermediate messages (progress notifications, arbitrary notifications) to
 * the client during a long-running operation.
 *
 * <p>This is a Mocapi concept wrapping an {@link OdysseyStream} with MCP-specific JSON-RPC
 * notification formatting.
 */
public class McpStreamContext {

  private static final String JSONRPC_VERSION = "2.0";

  private final OdysseyStream stream;
  private final ObjectMapper objectMapper;

  McpStreamContext(OdysseyStream stream, ObjectMapper objectMapper) {
    this.stream = stream;
    this.objectMapper = objectMapper;
  }

  /**
   * Sends a progress notification to the client.
   *
   * @param progress the current progress value
   * @param total the total expected value
   */
  public void sendProgress(long progress, long total) {
    ObjectNode notification = objectMapper.createObjectNode();
    notification.put("jsonrpc", JSONRPC_VERSION);
    notification.put("method", "notifications/progress");
    ObjectNode params = notification.putObject("params");
    params.put("progress", progress);
    params.put("total", total);
    stream.publishJson(notification);
  }

  /**
   * Sends an arbitrary notification to the client.
   *
   * @param method the notification method name
   * @param params the notification parameters (may be null)
   */
  public void sendNotification(String method, Object params) {
    ObjectNode notification = objectMapper.createObjectNode();
    notification.put("jsonrpc", JSONRPC_VERSION);
    notification.put("method", method);
    if (params != null) {
      notification.set("params", objectMapper.valueToTree(params));
    }
    stream.publishJson(notification);
  }
}
