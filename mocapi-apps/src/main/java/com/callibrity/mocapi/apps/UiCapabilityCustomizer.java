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

import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.discover.ServerCapabilitiesCustomizer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Declares the {@code io.modelcontextprotocol/ui} extension capability (MCP Apps). */
public class UiCapabilityCustomizer implements ServerCapabilitiesCustomizer {

  private static final String UI_EXTENSION_ID = "io.modelcontextprotocol/ui";
  private static final String RESOURCE_MIME_TYPE = "text/html;profile=mcp-app";

  private final ObjectMapper mapper;

  public UiCapabilityCustomizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void customize(ServerCapabilities.Builder capabilities) {
    ObjectNode config = mapper.createObjectNode();
    config.putArray("mimeTypes").add(RESOURCE_MIME_TYPE);
    capabilities.extension(UI_EXTENSION_ID, config);
  }
}
