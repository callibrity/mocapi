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
package com.callibrity.mocapi.api.tools;

import com.callibrity.mocapi.model.Tool;
import tools.jackson.databind.JsonNode;

/**
 * Runtime representation of a single MCP tool — the {@link #descriptor() descriptor} is what
 * clients see in {@code tools/list} (name, title, description, input/output schemas), and {@link
 * #call(JsonNode)} is what runs when the client sends {@code tools/call}. Registered tools are
 * discovered at startup via {@link McpToolProvider}.
 *
 * <p>Most applications declare tools with {@code @ToolService} + {@code @ToolMethod} on Spring
 * beans and never implement this interface directly — the annotation processor generates an {@code
 * McpTool} per annotated method. Implement this interface only when you need fully programmatic
 * control (dynamic tool catalogs, generated tools, etc.).
 */
public interface McpTool {

  /**
   * The descriptor advertised to clients in {@code tools/list}. Input arguments in {@link
   * #call(JsonNode)} are pre-validated against {@link Tool#inputSchema()} before this tool is
   * invoked.
   */
  Tool descriptor();

  /**
   * Invoke the tool with the client's arguments. The return value is serialized into the tool call
   * result's {@code structuredContent}; if you return a {@code CallToolResult} directly it is used
   * as-is, letting you set {@code isError} or supply explicit text content blocks.
   */
  Object call(JsonNode arguments);
}
