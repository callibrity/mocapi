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
package com.callibrity.mocapi.server;

import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.LoggingCapability;
import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;

public class ServerCapabilitiesBuilder {
  private ToolsCapability tools;
  private ResourcesCapability resources;
  private PromptsCapability prompts;
  private LoggingCapability logging;
  private CompletionsCapability completions;

  public ServerCapabilitiesBuilder tools(ToolsCapability tools) {
    this.tools = tools;
    return this;
  }

  public ServerCapabilitiesBuilder resources(ResourcesCapability resources) {
    this.resources = resources;
    return this;
  }

  public ServerCapabilitiesBuilder prompts(PromptsCapability prompts) {
    this.prompts = prompts;
    return this;
  }

  public ServerCapabilitiesBuilder logging(LoggingCapability logging) {
    this.logging = logging;
    return this;
  }

  public ServerCapabilitiesBuilder completions(CompletionsCapability completions) {
    this.completions = completions;
    return this;
  }

  public ServerCapabilities build() {
    return new ServerCapabilities(tools, logging, completions, resources, prompts);
  }
}
