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
package com.callibrity.mocapi.prompts;

import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@JsonRpcService
@RequiredArgsConstructor
public class McpPromptMethods {

  private final PromptsRegistry promptsRegistry;

  @JsonRpcMethod("prompts/list")
  public ListPromptsResponse listPrompts(String cursor) {
    return promptsRegistry.listPrompts(cursor);
  }

  @JsonRpcMethod("prompts/get")
  public GetPromptResponse getPrompt(String name, Map<String, String> arguments) {
    return promptsRegistry.lookup(name).get(arguments);
  }
}
