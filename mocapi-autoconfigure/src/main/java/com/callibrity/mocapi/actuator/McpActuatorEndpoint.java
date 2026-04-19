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
package com.callibrity.mocapi.actuator;

import com.callibrity.mocapi.actuator.McpActuatorSnapshot.Counts;
import com.callibrity.mocapi.actuator.McpActuatorSnapshot.ServerInfo;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Actuator endpoint returning a read-only inventory of the MCP handlers registered on this node.
 * Mapped to {@code /actuator/mcp} when exposed via {@code
 * management.endpoints.web.exposure.include}.
 *
 * <p>The endpoint publishes handler <em>names</em> and <em>schema digests</em>, never the full
 * schema bodies — full schemas are already available via the MCP protocol's {@code tools/list}.
 */
@Endpoint(id = "mcp")
public class McpActuatorEndpoint {

  // Latest MCP protocol version this server build targets. Individual sessions negotiate their
  // own version at initialize time; this field reports the build's target, not the per-session
  // choice.
  static final String PROTOCOL_VERSION = "2025-11-25";

  private final Implementation serverInfo;
  private final McpToolsService tools;
  private final McpPromptsService prompts;
  private final McpResourcesService resources;

  public McpActuatorEndpoint(
      Implementation serverInfo,
      McpToolsService tools,
      McpPromptsService prompts,
      McpResourcesService resources) {
    this.serverInfo = serverInfo;
    this.tools = tools;
    this.prompts = prompts;
    this.resources = resources;
  }

  @ReadOperation
  public McpActuatorSnapshot snapshot() {
    var toolInfos = tools.allDescriptors().stream().map(McpActuatorSnapshots::toToolInfo).toList();
    var promptInfos =
        prompts.allDescriptors().stream().map(McpActuatorSnapshots::toPromptInfo).toList();
    var resourceInfos =
        resources.allResourceDescriptors().stream()
            .map(McpActuatorSnapshots::toResourceInfo)
            .toList();
    var templateInfos =
        resources.allResourceTemplateDescriptors().stream()
            .map(McpActuatorSnapshots::toResourceTemplateInfo)
            .toList();

    return new McpActuatorSnapshot(
        new ServerInfo(serverInfo.name(), serverInfo.version(), PROTOCOL_VERSION),
        new Counts(
            toolInfos.size(), promptInfos.size(), resourceInfos.size(), templateInfos.size()),
        toolInfos,
        promptInfos,
        resourceInfos,
        templateInfos);
  }
}
