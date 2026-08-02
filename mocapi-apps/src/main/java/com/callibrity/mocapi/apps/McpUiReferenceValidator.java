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
package com.callibrity.mocapi.apps;

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Fails the application startup if any tool's {@code @McpUi} link points at a {@code ui://}
 * resource that no handler on this server declares. The link lives on the tool descriptor as {@code
 * _meta.ui.resourceUri} (written by {@link AppsToolUiMetaCustomizer}); this cross-checks each
 * against the URIs registered with {@link McpResourcesService}. A fat-fingered URI would otherwise
 * fail silently in the host at render time — this turns it into a clear error at boot.
 *
 * <p>Runs once, after all singletons are instantiated (so both services are fully built). It is a
 * no-op when there is no tools/resources service to validate against.
 */
public class McpUiReferenceValidator implements SmartInitializingSingleton {

  private final ObjectProvider<McpToolsService> toolsService;
  private final ObjectProvider<McpResourcesService> resourcesService;

  public McpUiReferenceValidator(
      ObjectProvider<McpToolsService> toolsService,
      ObjectProvider<McpResourcesService> resourcesService) {
    this.toolsService = toolsService;
    this.resourcesService = resourcesService;
  }

  @Override
  public void afterSingletonsInstantiated() {
    McpToolsService tools = toolsService.getIfAvailable();
    McpResourcesService resources = resourcesService.getIfAvailable();
    if (tools == null || resources == null) {
      return;
    }
    Set<String> declared = resources.resourceUris();
    List<String> violations = new ArrayList<>();
    for (Tool tool : tools.allToolDescriptors()) {
      String referenced = uiResourceUri(tool);
      if (referenced != null && !declared.contains(referenced)) {
        violations.add("  tool \"" + tool.name() + "\" links @McpUi(\"" + referenced + "\")");
      }
    }
    if (!violations.isEmpty()) {
      throw new IllegalStateException(
          "MCP Apps: "
              + violations.size()
              + " tool(s) reference a ui:// resource that is not declared on this server:\n"
              + String.join("\n", violations)
              + "\nDeclared resource URIs: "
              + new TreeSet<>(declared)
              + "\nEnsure each @McpUi(...) value matches a @McpAppResource / @McpResource uri.");
    }
  }

  private static String uiResourceUri(Tool tool) {
    ObjectNode meta = tool.meta();
    if (meta == null) {
      return null;
    }
    JsonNode ref = meta.path("ui").path("resourceUri");
    if (ref.isMissingNode() || ref.isNull()) {
      return null;
    }
    String uri = ref.asString();
    return uri.isBlank() ? null : uri;
  }
}
