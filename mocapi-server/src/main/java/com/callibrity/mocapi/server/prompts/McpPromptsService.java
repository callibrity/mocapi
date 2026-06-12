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

import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.ListPromptsResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.server.guards.Guards;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.util.PaginatedService;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages prompt registration and JSON-RPC dispatch. */
public class McpPromptsService extends PaginatedService<GetPromptHandler, Prompt> {

  private final Logger log = LoggerFactory.getLogger(McpPromptsService.class);
  private final MrtrElicitationEngine elicitationEngine;

  public McpPromptsService(List<GetPromptHandler> handlers, MrtrElicitationEngine engine) {
    this(handlers, engine, DEFAULT_PAGE_SIZE);
  }

  public McpPromptsService(
      List<GetPromptHandler> handlers, MrtrElicitationEngine engine, int pageSize) {
    super(
        handlers,
        GetPromptHandler::name,
        GetPromptHandler::descriptor,
        Comparator.comparing(Prompt::name),
        "Prompt",
        pageSize);
    this.elicitationEngine = engine;
  }

  @JsonRpcMethod(McpMethods.PROMPTS_LIST)
  public ListPromptsResult listPrompts(@JsonRpcParams PaginatedRequestParams params) {
    // Conservative cache defaults until Phase 5 makes them configurable (ttlMs=0, private).
    return paginate(
        h -> Guards.allows(h.guards()),
        params,
        (prompts, nextCursor) ->
            new ListPromptsResult(
                prompts, nextCursor, 0L, CacheScope.PRIVATE, ResultTypes.COMPLETE));
  }

  /**
   * Returns either a {@link GetPromptResult} or an {@code InputRequiredResult} — the MRTR union the
   * spec declares for {@code prompts/get} responses — so the declared type is {@link Object};
   * ripcurl serializes the runtime type. The {@link MrtrElicitationEngine} wraps the invocation:
   * this method is one of the exactly three MRTR-capable RPC seams (see the engine's javadoc).
   */
  @JsonRpcMethod(McpMethods.PROMPTS_GET)
  public Object getPrompt(@JsonRpcParams GetPromptRequestParams params) {
    String name = params.name();
    log.debug("Received request to get prompt \"{}\"", name);
    GetPromptHandler handler = lookup(name);
    Map<String, String> arguments = params.arguments() != null ? params.arguments() : Map.of();
    return elicitationEngine.execute(
        McpMethods.PROMPTS_GET,
        params,
        params.inputResponses(),
        params.requestState(),
        () -> handler.get(arguments));
  }
}
