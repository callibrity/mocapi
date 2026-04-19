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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.ToolService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

/**
 * Tool demonstrating Jakarta Bean Validation on a {@code @McpTool} parameter. With the {@code
 * mocapi-jakarta-validation-spring-boot-starter} on the classpath, methodical's invoker runs the
 * validator before the method body executes; violations surface as {@code CallToolResult { isError:
 * true, content: [text] }} — the MCP-spec-idiomatic shape for tool execution errors, so the calling
 * LLM can read the message and retry with adjusted arguments.
 *
 * <p>Invoke with a blank or too-short {@code name} to observe the validated error:
 *
 * <pre>{@code
 * POST /mcp
 * {"jsonrpc":"2.0","id":1,"method":"tools/call",
 *  "params":{"name":"greet","arguments":{"name":""}}}
 *
 * → 200 OK
 * {"jsonrpc":"2.0","id":1,
 *  "result":{"content":[{"type":"text",
 *    "text":"greet.name: must not be blank, greet.name: size must be between 2 and 60"}],
 *   "isError":true}}
 * }</pre>
 */
@Component
@ToolService
public class GreetTool {

  @McpTool(name = "greet", description = "Returns a greeting; name must be 2-60 non-blank chars")
  public GreetResponse greet(@NotBlank @Size(min = 2, max = 60) String name) {
    return new GreetResponse("Hello, " + name + "!");
  }

  public record GreetResponse(String message) {}
}
