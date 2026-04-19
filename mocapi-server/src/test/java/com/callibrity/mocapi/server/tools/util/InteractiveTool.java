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
package com.callibrity.mocapi.server.tools.util;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.LoggingLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;

@Component
public class InteractiveTool {
  @McpTool(name = "interactive-greet", description = "Greets with progress")
  public HelloResponse greet(
      @Schema(description = "The name to greet") String name, McpToolContext ctx) {
    ctx.sendProgress(1, 2);
    ctx.log(LoggingLevel.INFO, "interactive-greet", "Processing " + name);
    ctx.sendProgress(2, 2);
    return new HelloResponse(String.format("Hello, %s!", name));
  }
}
