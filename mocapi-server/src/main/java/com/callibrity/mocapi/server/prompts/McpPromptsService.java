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
package com.callibrity.mocapi.server.prompts;

import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.ListPromptsResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.server.util.PaginatedService;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Manages prompt registration and JSON-RPC dispatch. */
@JsonRpcService
@lombok.extern.slf4j.Slf4j
public class McpPromptsService extends PaginatedService<GetPromptHandler, Prompt> {

  public McpPromptsService(List<GetPromptHandler> handlers) {
    this(handlers, DEFAULT_PAGE_SIZE);
  }

  public McpPromptsService(List<GetPromptHandler> handlers, int pageSize) {
    super(
        handlers,
        GetPromptHandler::name,
        GetPromptHandler::descriptor,
        Comparator.comparing(Prompt::name),
        "Prompt",
        pageSize);
  }

  @JsonRpcMethod(McpMethods.PROMPTS_LIST)
  public ListPromptsResult listPrompts(@JsonRpcParams PaginatedRequestParams params) {
    return paginate(params, ListPromptsResult::new);
  }

  @JsonRpcMethod(McpMethods.PROMPTS_GET)
  public GetPromptResult getPrompt(@JsonRpcParams GetPromptRequestParams params) {
    String name = params.name();
    log.debug("Received request to get prompt \"{}\"", name);
    GetPromptHandler handler = lookup(name);
    Map<String, String> arguments = params.arguments() != null ? params.arguments() : Map.of();
    return handler.get(arguments);
  }
}
