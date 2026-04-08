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
package com.callibrity.mocapi.tools;

import com.callibrity.ripcurl.core.annotation.JsonRpc;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@JsonRpcService
@RequiredArgsConstructor
public class McpToolMethods {

  private final ToolsRegistry toolsRegistry;
  private final ObjectMapper objectMapper;
  private final ToolMethodInvoker toolMethodInvoker;

  @JsonRpc("tools/list")
  public ToolsRegistry.ListToolsResponse listTools(String cursor) {
    return toolsRegistry.listTools(cursor);
  }

  @JsonRpc("tools/call")
  public ToolsRegistry.CallToolResponse callTool(
      String name, ObjectNode arguments, ObjectNode _meta) {
    ObjectNode args = arguments != null ? arguments : objectMapper.createObjectNode();
    String progressToken = extractProgressToken(_meta);
    return toolMethodInvoker.invoke(name, args, progressToken);
  }

  private static String extractProgressToken(ObjectNode meta) {
    if (meta == null) return null;
    JsonNode token = meta.get("progressToken");
    return (token == null || token.isMissingNode()) ? null : token.asString();
  }
}
