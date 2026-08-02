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
import com.callibrity.mocapi.server.tools.CallToolHandlerConfig;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import java.lang.reflect.Method;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Writes a tool's {@code _meta.ui} from an {@link McpUi} annotation, when present (ADR-0039). */
public class AppsToolUiMetaCustomizer implements CallToolHandlerCustomizer {

  private final ObjectMapper mapper;

  public AppsToolUiMetaCustomizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void customize(CallToolHandlerConfig config) {
    Method method = config.method();
    McpUi ui = method.getAnnotation(McpUi.class);
    if (ui == null) {
      return;
    }
    McpUiToolMeta uiMeta = new McpUiToolMeta(ui.value(), List.of(ui.visibility()));
    Tool descriptor = config.descriptor();
    ObjectNode meta = descriptor.meta() != null ? descriptor.meta() : mapper.createObjectNode();
    meta.set("ui", mapper.valueToTree(uiMeta));
    config.descriptor(descriptor.withMeta(meta));
  }
}
