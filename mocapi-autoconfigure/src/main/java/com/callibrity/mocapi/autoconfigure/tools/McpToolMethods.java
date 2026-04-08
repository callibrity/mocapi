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
package com.callibrity.mocapi.autoconfigure.tools;

import com.callibrity.mocapi.tools.McpToolsCapability;
import com.callibrity.ripcurl.core.annotation.JsonRpc;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@JsonRpcService
@RequiredArgsConstructor
public class McpToolMethods {

  private final McpToolsCapability toolsCapability;
  private final ObjectMapper objectMapper;

  @JsonRpc("tools/list")
  public McpToolsCapability.ListToolsResponse listTools(String cursor) {
    return toolsCapability.listTools(cursor);
  }

  @JsonRpc("tools/call")
  public McpToolsCapability.CallToolResponse callTool(String name, ObjectNode arguments) {
    return toolsCapability.callTool(
        name, arguments != null ? arguments : objectMapper.createObjectNode());
  }
}
