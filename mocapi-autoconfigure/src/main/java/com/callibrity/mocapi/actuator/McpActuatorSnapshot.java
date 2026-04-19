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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Read-only snapshot of the MCP inventory exposed on this node. Returned from {@code GET
 * /actuator/mcp}. All fields are plain data; null fields are omitted from the JSON so environments
 * without {@code BuildProperties} (no {@code server.version}) don't publish "unknown" noise.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpActuatorSnapshot(
    ServerInfo server,
    Counts counts,
    List<ToolInfo> tools,
    List<PromptInfo> prompts,
    List<ResourceInfo> resources,
    List<ResourceTemplateInfo> resourceTemplates) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ServerInfo(String name, String version, String protocolVersion) {}

  public record Counts(int tools, int prompts, int resources, int resourceTemplates) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ToolInfo(
      String name,
      String title,
      String description,
      String inputSchemaDigest,
      String outputSchemaDigest) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PromptInfo(
      String name, String title, String description, List<PromptArgumentInfo> arguments) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PromptArgumentInfo(String name, Boolean required) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ResourceInfo(String uri, String name, String description, String mimeType) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ResourceTemplateInfo(
      String uriTemplate, String name, String description, String mimeType) {}
}
