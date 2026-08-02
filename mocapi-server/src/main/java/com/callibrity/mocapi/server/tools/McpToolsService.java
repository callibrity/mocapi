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
package com.callibrity.mocapi.server.tools;

import static com.callibrity.mocapi.model.McpMethods.TOOLS_CALL;
import static com.callibrity.mocapi.model.McpMethods.TOOLS_LIST;

import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.ListToolsResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.dispatch.DispatchChains;
import com.callibrity.mocapi.server.dispatch.McpDispatchInterceptor;
import com.callibrity.mocapi.server.guards.Guards;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.ReplayExecutor;
import com.callibrity.mocapi.server.util.PaginatedService;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Manages tool registration and JSON-RPC dispatch. Input validation runs as a per-handler
 * interceptor wired into each {@link CallToolHandler}'s invoker; see {@link
 * InputSchemaValidatingInterceptor}. Invocation mechanics (context construction, the exception
 * cascade, result mapping) live in {@link ToolInvocationCore}, which this service's sync path
 * delegates to (ADR-0039).
 */
public class McpToolsService extends PaginatedService<CallToolHandler, Tool> {

  private final Logger log = LoggerFactory.getLogger(McpToolsService.class);
  private final ObjectMapper objectMapper;
  private final MrtrElicitationEngine elicitationEngine;
  private final CacheSettings cacheSettings;
  private final List<McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>>
      dispatchInterceptors;
  private final ToolInvocationCore core;

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine) {
    this(handlers, objectMapper, elicitationEngine, DEFAULT_PAGE_SIZE, CacheSettings.defaults());
  }

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      int pageSize) {
    this(handlers, objectMapper, elicitationEngine, pageSize, CacheSettings.defaults());
  }

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      int pageSize,
      CacheSettings cacheSettings) {
    this(handlers, objectMapper, elicitationEngine, pageSize, cacheSettings, List.of());
  }

  public McpToolsService(
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      int pageSize,
      CacheSettings cacheSettings,
      List<McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>> dispatchInterceptors) {
    this(
        new ToolInvocationCore(
            handlers, objectMapper, elicitationEngine, new ReplayExecutor(objectMapper)),
        handlers,
        objectMapper,
        elicitationEngine,
        pageSize,
        cacheSettings,
        dispatchInterceptors);
  }

  /**
   * Primary constructor (ADR-0039): the {@link ToolInvocationCore} is constructor-injected so
   * autoconfigure can share one core instance with other detached carriers (e.g. {@code
   * TaskExecutionEngine}) instead of each building its own.
   */
  public McpToolsService(
      ToolInvocationCore core,
      List<CallToolHandler> handlers,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      int pageSize,
      CacheSettings cacheSettings,
      List<McpDispatchInterceptor<CallToolHandler, CallToolRequestParams>> dispatchInterceptors) {
    super(
        handlers,
        CallToolHandler::name,
        CallToolHandler::descriptor,
        Comparator.comparing(Tool::name),
        "Tool",
        pageSize);
    this.objectMapper = objectMapper;
    this.elicitationEngine = elicitationEngine;
    this.cacheSettings = cacheSettings;
    this.dispatchInterceptors = DispatchChains.sort(dispatchInterceptors);
    this.core = core;
  }

  /**
   * Lists registered tools sorted by tool name — the deterministic ordering the spec recommends so
   * clients can cache list responses and LLM prompt caches get stable prefixes. Cache directives
   * ({@code ttlMs}/{@code cacheScope}) come from the configured {@link CacheSettings} list values.
   */
  @JsonRpcMethod(TOOLS_LIST)
  public ListToolsResult listTools(@JsonRpcParams PaginatedRequestParams params) {
    return paginate(
        h -> Guards.allows(h.guards()),
        params,
        (tools, nextCursor) ->
            new ListToolsResult(
                tools,
                nextCursor,
                cacheSettings.listTtlMs(),
                cacheSettings.scope(),
                ResultTypes.COMPLETE));
  }

  /** Returns the full {@link Tool} descriptor for a registered tool, or {@code null} if none. */
  public Tool findToolDescriptor(String name) {
    return findByName(name).map(CallToolHandler::descriptor).orElse(null);
  }

  /** Returns every registered tool descriptor, sorted by name. */
  public List<Tool> allToolDescriptors() {
    return allDescriptors();
  }

  /**
   * Returns either a {@link com.callibrity.mocapi.model.CallToolResult} or an {@code
   * InputRequiredResult} — the MRTR union the spec declares for {@code tools/call} responses — so
   * the declared type is {@link Object}; ripcurl serializes the runtime type. The {@link
   * MrtrElicitationEngine} wraps the invocation: this method is one of the exactly three
   * MRTR-capable RPC seams (see the engine's javadoc).
   */
  @JsonRpcMethod(TOOLS_CALL)
  public Object callTool(@JsonRpcParams CallToolRequestParams params) {
    String name = params.name();
    log.debug("Received request to call tool \"{}\"", name);
    JsonNode args =
        params.arguments() != null ? params.arguments() : objectMapper.createObjectNode();
    CallToolHandler handler = lookup(name);
    return DispatchChains.run(
        dispatchInterceptors,
        handler,
        params,
        () ->
            elicitationEngine.execute(
                TOOLS_CALL,
                params,
                params.inputResponses(),
                params.requestState(),
                () -> core.invokeTool(name, handler, args, params)));
  }
}
