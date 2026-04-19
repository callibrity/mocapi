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
package com.callibrity.mocapi.examples.validation;

import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resource template demonstrating Jakarta Bean Validation on a URI template variable. The template
 * is {@code config://{env}/app}; the {@code env} variable extracted from the URI must match {@code
 * ^[a-z]+$}. Violations surface as JSON-RPC {@code -32602 Invalid params} with per-violation {@code
 * data}.
 *
 * <p>Invoke with a URI whose {@code env} segment violates the pattern to observe the validated
 * error:
 *
 * <pre>{@code
 * POST /mcp
 * {"jsonrpc":"2.0","id":1,"method":"resources/read",
 *  "params":{"uri":"config://DEV/app"}}
 *
 * → 200 OK
 * {"jsonrpc":"2.0","id":1,
 *  "error":{"code":-32602,"message":"Invalid params",
 *    "data":[{"field":"config.env","message":"must match \"^[a-z]+$\""}]}}
 *
 * # Succeeds:
 * {"jsonrpc":"2.0","id":1,"method":"resources/read",
 *  "params":{"uri":"config://dev/app"}}
 * }</pre>
 */
@Component
@ResourceService
public class ConfigResources {

  @McpResourceTemplate(
      uriTemplate = "config://{env}/app",
      name = "Per-environment config",
      description = "Returns stub config; env must be lowercase letters only",
      mimeType = "text/plain")
  public ReadResourceResult config(@Pattern(regexp = "^[a-z]+$") String env) {
    return new ReadResourceResult(
        List.of(
            new TextResourceContents(
                "config://" + env + "/app",
                "text/plain",
                "env=" + env + "\nendpoint=https://api." + env + ".example.com")));
  }
}
