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
package com.callibrity.mocapi.server.resources;

import com.callibrity.mocapi.api.elicitation.McpElicitor;
import com.callibrity.mocapi.api.resources.McpResourceContext;
import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.ListResourceTemplatesResult;
import com.callibrity.mocapi.model.ListResourcesResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.dispatch.DispatchChains;
import com.callibrity.mocapi.server.dispatch.McpDispatchInterceptor;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.guards.Guards;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.util.Cursors;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;
import tools.jackson.databind.node.ValueNode;

/** Manages resource registration, lookup, pagination, and JSON-RPC dispatch. */
public class McpResourcesService {

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Logger log = LoggerFactory.getLogger(McpResourcesService.class);
  private final Map<String, ReadResourceHandler> resources;
  private final Map<UriTemplate, ReadResourceTemplateHandler> templates;
  private final List<ReadResourceHandler> sortedResources;
  private final List<ReadResourceTemplateHandler> sortedTemplates;
  private final int pageSize;
  private final MrtrElicitationEngine elicitationEngine;
  private final CacheSettings cacheSettings;
  private final List<McpDispatchInterceptor<ReadResourceHandler, ResourceRequestParams>>
      dispatchInterceptors;

  /**
   * Convenience constructor: no dispatch interceptors, default page size, default cache settings.
   */
  public McpResourcesService(List<ResourceContributor> contributors, MrtrElicitationEngine engine) {
    this(contributors, engine, DEFAULT_PAGE_SIZE, CacheSettings.defaults(), List.of());
  }

  /**
   * Construction-time merge of every {@link ResourceContributor} (ADR-0035), plus the {@code
   * resources/read} {@link McpDispatchInterceptor} chain: the service is built once from the
   * flattened union of the contributors' resources and templates. There is no runtime registration;
   * the maps stay immutable. This is the primary constructor (ADR-0039); the two-arg overload is a
   * convenience for the common no-interceptor, default-settings case.
   */
  public McpResourcesService(
      List<ResourceContributor> contributors,
      MrtrElicitationEngine engine,
      int pageSize,
      CacheSettings cacheSettings,
      List<McpDispatchInterceptor<ReadResourceHandler, ResourceRequestParams>>
          dispatchInterceptors) {
    this(
        mergeResources(contributors),
        mergeTemplates(contributors),
        engine,
        pageSize,
        cacheSettings,
        dispatchInterceptors);
  }

  /**
   * Merges every contributor's {@link ResourceContributor#resources()} into one URI-keyed map,
   * failing fast with an {@link IllegalStateException} naming both contributors (mirrors {@code
   * StreamableHttpAutoConfiguration}'s {@code RoutedParamContributor} merge) when two contributors
   * — or one contributor twice — claim the same URI. Contributors are walked one at a time (rather
   * than flattened via a single stream) specifically so the offending contributor's identity is
   * still in hand at the point a collision is detected.
   */
  private static Map<String, ReadResourceHandler> mergeResources(
      List<ResourceContributor> contributors) {
    Map<String, ReadResourceHandler> merged = new LinkedHashMap<>();
    Map<String, String> contributedBy = new LinkedHashMap<>();
    for (ResourceContributor contributor : contributors) {
      String contributorName = identify(contributor);
      for (ReadResourceHandler handler : contributor.resources()) {
        String uri = handler.uri();
        String existing = contributedBy.putIfAbsent(uri, contributorName);
        if (existing != null) {
          throw new IllegalStateException(
              "ResourceContributor \""
                  + existing
                  + "\" and \""
                  + contributorName
                  + "\" both contribute resource URI \""
                  + uri
                  + "\"");
        }
        merged.put(uri, handler);
      }
    }
    return merged;
  }

  /** Template counterpart of {@link #mergeResources(List)}; see that method for the rationale. */
  private static Map<String, ReadResourceTemplateHandler> mergeTemplates(
      List<ResourceContributor> contributors) {
    Map<String, ReadResourceTemplateHandler> merged = new LinkedHashMap<>();
    Map<String, String> contributedBy = new LinkedHashMap<>();
    for (ResourceContributor contributor : contributors) {
      String contributorName = identify(contributor);
      for (ReadResourceTemplateHandler handler : contributor.resourceTemplates()) {
        String uriTemplate = handler.uriTemplate();
        String existing = contributedBy.putIfAbsent(uriTemplate, contributorName);
        if (existing != null) {
          throw new IllegalStateException(
              "ResourceContributor \""
                  + existing
                  + "\" and \""
                  + contributorName
                  + "\" both contribute URI template \""
                  + uriTemplate
                  + "\"");
        }
        merged.put(uriTemplate, handler);
      }
    }
    return merged;
  }

  /**
   * Names a contributor for a collision message. Falls back to the fully qualified name for
   * anonymous contributors (e.g. {@link ResourceContributor#of}'s adapter), whose simple name is
   * blank.
   */
  private static String identify(ResourceContributor contributor) {
    Class<?> type = contributor.getClass();
    String simpleName = type.getSimpleName();
    return simpleName.isEmpty() ? type.getName() : simpleName;
  }

  private McpResourcesService(
      Map<String, ReadResourceHandler> resources,
      Map<String, ReadResourceTemplateHandler> templatesByString,
      MrtrElicitationEngine engine,
      int pageSize,
      CacheSettings cacheSettings,
      List<McpDispatchInterceptor<ReadResourceHandler, ResourceRequestParams>>
          dispatchInterceptors) {
    this.resources = resources;
    this.sortedResources =
        resources.values().stream().sorted(Comparator.comparing(ReadResourceHandler::uri)).toList();

    this.templates = new LinkedHashMap<>();
    templatesByString.forEach(
        (uriTemplate, handler) -> this.templates.put(new UriTemplate(uriTemplate), handler));
    this.sortedTemplates =
        templatesByString.values().stream()
            .sorted(Comparator.comparing(ReadResourceTemplateHandler::uriTemplate))
            .toList();
    this.pageSize = pageSize;
    this.elicitationEngine = engine;
    this.cacheSettings = cacheSettings;
    this.dispatchInterceptors = DispatchChains.sort(dispatchInterceptors);
  }

  /**
   * Lists registered resources sorted by URI — the resource's identity, matching what the {@code
   * Mcp-Name} routing header carries for {@code resources/read}. Deterministic ordering is the
   * spec's recommendation so clients can cache list responses and LLM prompt caches get stable
   * prefixes. Cache directives ({@code ttlMs}/{@code cacheScope}) come from the configured {@link
   * CacheSettings} list values.
   */
  @JsonRpcMethod(McpMethods.RESOURCES_LIST)
  public ListResourcesResult listResources(@JsonRpcParams PaginatedRequestParams params) {
    List<Resource> visible =
        sortedResources.stream()
            .filter(h -> Guards.allows(h.guards()))
            .map(ReadResourceHandler::descriptor)
            .toList();
    return Cursors.paginate(
        visible,
        params,
        pageSize,
        (pageResources, nextCursor) ->
            new ListResourcesResult(
                pageResources,
                nextCursor,
                cacheSettings.listTtlMs(),
                cacheSettings.scope(),
                ResultTypes.COMPLETE));
  }

  /**
   * Lists registered resource templates sorted by URI template — the template's identity (templates
   * have no other unique key). Deterministic ordering is the spec's recommendation so clients can
   * cache list responses and LLM prompt caches get stable prefixes. Cache directives ({@code
   * ttlMs}/{@code cacheScope}) come from the configured {@link CacheSettings} list values.
   */
  @JsonRpcMethod(McpMethods.RESOURCES_TEMPLATES_LIST)
  public ListResourceTemplatesResult listResourceTemplates(
      @JsonRpcParams PaginatedRequestParams params) {
    List<ResourceTemplate> visible =
        sortedTemplates.stream()
            .filter(h -> Guards.allows(h.guards()))
            .map(ReadResourceTemplateHandler::descriptor)
            .toList();
    return Cursors.paginate(
        visible,
        params,
        pageSize,
        (resourceTemplates, nextCursor) ->
            new ListResourceTemplatesResult(
                resourceTemplates,
                nextCursor,
                cacheSettings.listTtlMs(),
                cacheSettings.scope(),
                ResultTypes.COMPLETE));
  }

  /**
   * Returns either a {@link ReadResourceResult} or an {@code InputRequiredResult} — the MRTR union
   * the spec declares for {@code resources/read} responses — so the declared type is {@link
   * Object}; ripcurl serializes the runtime type. The {@link MrtrElicitationEngine} wraps the
   * invocation: this method is one of the exactly three MRTR-capable RPC seams (see the engine's
   * javadoc).
   *
   * <p>The {@link McpDispatchInterceptor} chain only sees requests that resolve to a fixed {@link
   * ReadResourceHandler} — the interceptor's {@code H} type parameter. A URI that resolves via a
   * {@link ReadResourceTemplateHandler} instead has no {@link Resource}-typed descriptor to hand
   * the chain, so template-matched reads bypass the interceptor seam entirely and always take the
   * default path (still through the MRTR {@link MrtrElicitationEngine}). This is a deliberate scope
   * limit, not an oversight: see ADR-0039.
   */
  @JsonRpcMethod(McpMethods.RESOURCES_READ)
  public Object readResource(@JsonRpcParams ResourceRequestParams params) {
    String uri = params.uri();
    log.debug("Received request to read resource \"{}\"", uri);
    McpTransport transport = McpTransport.CURRENT.isBound() ? McpTransport.CURRENT.get() : null;
    McpExchange exchange = McpExchange.CURRENT.isBound() ? McpExchange.CURRENT.get() : null;
    ValueNode progressToken = params.meta() != null ? params.meta().progressToken() : null;
    DefaultMcpResourceContext ctx =
        new DefaultMcpResourceContext(transport, progressToken, elicitationEngine, exchange, uri);
    Supplier<Object> defaultPath =
        () ->
            elicitationEngine.execute(
                McpMethods.RESOURCES_READ,
                params,
                params.inputResponses(),
                params.requestState(),
                () ->
                    ScopedValue.where(McpResourceContext.CURRENT, ctx)
                        .where(McpElicitor.CURRENT, ctx)
                        .call(() -> doReadResource(uri)));
    ReadResourceHandler exact = resources.get(uri);
    if (exact == null) {
      return defaultPath.get();
    }
    return DispatchChains.run(dispatchInterceptors, exact, params, defaultPath);
  }

  private ReadResourceResult doReadResource(String uri) {
    ReadResourceHandler exact = resources.get(uri);
    if (exact != null) {
      return withConfiguredCacheDefaults(exact.read());
    }

    for (var entry : templates.entrySet()) {
      if (entry.getKey().matches(uri)) {
        ReadResourceTemplateHandler handler = entry.getValue();
        return withConfiguredCacheDefaults(handler.read(uri, entry.getKey().match(uri)));
      }
    }

    throw new JsonRpcException(
        JsonRpcProtocol.INVALID_PARAMS, String.format("Resource not found: %s", uri));
  }

  /**
   * Applies the configured read-cache directives to a handler-produced result that carries the
   * conservative defaults ({@code ttlMs=0}, {@code private}) — the values the {@link
   * ReadResourceResult#ofText} / {@code ofBlob} convenience factories stamp. A handler that
   * constructs its result with any other ttl/scope combination has expressed an explicit
   * per-resource cache policy, which wins over the server-wide configuration.
   */
  private ReadResourceResult withConfiguredCacheDefaults(ReadResourceResult result) {
    if (result == null || result.ttlMs() != 0L || result.cacheScope() != CacheScope.PRIVATE) {
      return result;
    }
    return new ReadResourceResult(
        result.contents(), cacheSettings.readTtlMs(), cacheSettings.scope(), result.resultType());
  }

  public boolean isEmpty() {
    return resources.isEmpty() && templates.isEmpty();
  }

  /**
   * The URIs of every registered fixed resource, for cross-referencing declared resources (e.g. an
   * MCP Apps tool's {@code _meta.ui.resourceUri}). Templated resources are not included.
   */
  public Set<String> resourceUris() {
    return Set.copyOf(resources.keySet());
  }

  /** Returns every registered resource handler in sorted URI order. Unfiltered. */
  public List<ReadResourceHandler> allResourceHandlers() {
    return sortedResources;
  }

  /**
   * Returns every registered resource-template handler in sorted URI-template order. Unfiltered.
   */
  public List<ReadResourceTemplateHandler> allResourceTemplateHandlers() {
    return sortedTemplates;
  }
}
