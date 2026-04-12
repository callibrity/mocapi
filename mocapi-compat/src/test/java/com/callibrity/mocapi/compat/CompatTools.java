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
package com.callibrity.mocapi.compat;

import com.callibrity.mocapi.server.tools.McpToolContext;
import com.callibrity.mocapi.server.tools.annotation.McpToolParams;
import com.callibrity.mocapi.server.tools.annotation.ToolMethod;
import com.callibrity.mocapi.server.tools.annotation.ToolService;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Map;

@ToolService
public class CompatTools {

  @ToolMethod(name = "echo", description = "Returns its input as structured content")
  public Map<String, Object> echo(String message) {
    return Map.of("message", message);
  }

  @ToolMethod(name = "error", description = "Always throws a JSON-RPC error")
  public Map<String, Object> error() {
    throw new JsonRpcException(JsonRpcProtocol.INTERNAL_ERROR, "intentional error");
  }

  @ToolMethod(name = "stream", description = "A streaming tool that sends a progress notification")
  public void stream(String message, McpToolContext<Map<String, Object>> ctx) {
    ctx.sendProgress(1, 2);
    ctx.sendResult(Map.of("message", message));
  }

  public record GreetRequest(String name, int volume) {}

  @ToolMethod(name = "greet-record", description = "A tool that uses @McpToolParams record binding")
  public Map<String, Object> greetRecord(@McpToolParams GreetRequest request) {
    return Map.of("greeting", request.name().repeat(request.volume()));
  }
}
