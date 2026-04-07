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

import com.callibrity.mocapi.server.McpStreamContext;
import org.jwcarman.odyssey.core.OdysseyStream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Default implementation of {@link McpStreamContext} that wraps an {@link OdysseyStream} with
 * MCP-specific JSON-RPC notification formatting.
 */
public class DefaultMcpStreamContext implements McpStreamContext {

  private static final String JSONRPC_VERSION = "2.0";

  private final OdysseyStream stream;
  private final ObjectMapper objectMapper;

  DefaultMcpStreamContext(OdysseyStream stream, ObjectMapper objectMapper) {
    this.stream = stream;
    this.objectMapper = objectMapper;
  }

  @Override
  public void sendProgress(long progress, long total) {
    ObjectNode notification = objectMapper.createObjectNode();
    notification.put("jsonrpc", JSONRPC_VERSION);
    notification.put("method", "notifications/progress");
    ObjectNode params = notification.putObject("params");
    params.put("progress", progress);
    params.put("total", total);
    stream.publishJson(notification);
  }

  @Override
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
