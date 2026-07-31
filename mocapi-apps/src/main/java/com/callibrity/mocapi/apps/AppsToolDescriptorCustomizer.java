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
import com.callibrity.mocapi.server.tools.ToolDescriptorCustomizer;
import java.lang.reflect.Method;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Writes a tool's {@code _meta.ui} from an {@link McpUi} annotation, when present. */
public class AppsToolDescriptorCustomizer implements ToolDescriptorCustomizer {

  private final ObjectMapper mapper;

  public AppsToolDescriptorCustomizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Tool customize(Method method, Tool descriptor) {
    McpUi ui = method.getAnnotation(McpUi.class);
    if (ui == null) {
      return descriptor;
    }
    McpUiToolMeta uiMeta = new McpUiToolMeta(ui.value(), List.of(ui.visibility()));
    ObjectNode meta = descriptor.meta() != null ? descriptor.meta() : mapper.createObjectNode();
    meta.set("ui", mapper.valueToTree(uiMeta));
    return descriptor.withMeta(meta);
  }
}
