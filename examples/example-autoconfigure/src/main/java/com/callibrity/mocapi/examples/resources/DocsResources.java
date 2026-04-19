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
package com.callibrity.mocapi.examples.resources;

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import java.util.List;

/**
 * Example resource bean covering both fixed and templated resources. The templated variant uses an
 * enum for the {@code stage} path variable so MCP clients asking {@code completion/complete} for
 * the variable get {@code [DEV, STAGE, PROD]}, prefix-filtered.
 */
public class DocsResources {

  public enum Stage {
    DEV,
    STAGE,
    PROD
  }

  @McpResource(
      uri = "docs://readme",
      name = "README",
      description = "The top-level README for this example server",
      mimeType = "text/markdown")
  public ReadResourceResult readme() {
    return new ReadResourceResult(
        List.of(
            new TextResourceContents(
                "docs://readme",
                "text/markdown",
                "# Mocapi Example\n\nThis server demonstrates tools, prompts, and resources.")));
  }

  @McpResourceTemplate(
      uriTemplate = "env://{stage}/config",
      name = "Environment Config",
      description = "Returns a stub environment config document",
      mimeType = "text/plain")
  public ReadResourceResult config(Stage stage) {
    return new ReadResourceResult(
        List.of(
            new TextResourceContents(
                "env://" + stage + "/config",
                "text/plain",
                "stage="
                    + stage
                    + "\nendpoint=https://api."
                    + stage.name().toLowerCase()
                    + ".example.com")));
  }
}
