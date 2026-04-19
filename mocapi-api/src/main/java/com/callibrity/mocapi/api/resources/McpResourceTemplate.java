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
package com.callibrity.mocapi.api.resources;

import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResourceTemplate;
import java.util.Map;

/**
 * Runtime representation of a parameterized MCP resource — the {@link #descriptor() descriptor}
 * carries a URI template (e.g. {@code file:///logs/{date}}) advertised in {@code
 * resources/templates/list}, and {@link #read(Map)} resolves a concrete read request by filling in
 * the template variables. Registered via {@link McpResourceTemplateProvider}.
 *
 * <p>Use {@link McpResource} for fixed (non-parameterized) URIs. Most applications declare resource
 * templates with {@code @ResourceTemplateMethod} and never implement this SPI directly.
 */
public interface McpResourceTemplate {

  /** The template descriptor advertised to clients in {@code resources/templates/list}. */
  ResourceTemplate descriptor();

  /**
   * Read the resource for a concrete set of path-variable bindings extracted from the requested
   * URI. Keys in {@code pathVariables} correspond to the {@code {name}} placeholders in {@link
   * ResourceTemplate#uriTemplate()}.
   */
  ReadResourceResult read(Map<String, String> pathVariables);
}
