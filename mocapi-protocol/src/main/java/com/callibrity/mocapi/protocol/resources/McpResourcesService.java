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
package com.callibrity.mocapi.protocol.resources;

import com.callibrity.mocapi.model.EmptyResult;
import com.callibrity.mocapi.model.ListResourceTemplatesResult;
import com.callibrity.mocapi.model.ListResourcesResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.web.util.UriTemplate;

/** Manages resource registration, lookup, pagination, subscriptions, and JSON-RPC dispatch. */
@JsonRpcService
public class McpResourcesService {

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Map<String, McpResource> resources;
  private final Map<UriTemplate, McpResourceTemplate> templates;
  private final List<Resource> sortedResourceDescriptors;
  private final List<ResourceTemplate> sortedTemplateDescriptors;
  private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
  private final int pageSize;

  public McpResourcesService(
      List<McpResourceProvider> resourceProviders,
      List<McpResourceTemplateProvider> templateProviders) {
    this(resourceProviders, templateProviders, DEFAULT_PAGE_SIZE);
  }

  public McpResourcesService(
      List<McpResourceProvider> resourceProviders,
      List<McpResourceTemplateProvider> templateProviders,
      int pageSize) {
    var allResources =
        resourceProviders.stream()
            .flatMap(provider -> provider.getMcpResources().stream())
            .toList();
    this.resources =
        allResources.stream().collect(Collectors.toMap(r -> r.descriptor().uri(), r -> r));
    this.sortedResourceDescriptors =
        allResources.stream()
            .map(McpResource::descriptor)
            .sorted(Comparator.comparing(Resource::uri))
            .toList();

    var allTemplates =
        templateProviders.stream()
            .flatMap(provider -> provider.getMcpResourceTemplates().stream())
            .toList();
    this.templates =
        allTemplates.stream()
            .collect(
                Collectors.toMap(
                    t -> new UriTemplate(t.descriptor().uriTemplate()),
                    t -> t,
                    (a, b) -> {
                      throw new IllegalArgumentException(
                          "Duplicate URI template: " + a.descriptor().uriTemplate());
                    },
                    LinkedHashMap::new));
    this.sortedTemplateDescriptors =
        allTemplates.stream()
            .map(McpResourceTemplate::descriptor)
            .sorted(Comparator.comparing(ResourceTemplate::uriTemplate))
            .toList();
    this.pageSize = pageSize;
  }

  @JsonRpcMethod("resources/list")
  public ListResourcesResult listResources(@JsonRpcParams PaginatedRequestParams params) {
    var page = paginate(sortedResourceDescriptors, params);
    return new ListResourcesResult(page.items(), page.nextCursor());
  }

  @JsonRpcMethod("resources/templates/list")
  public ListResourceTemplatesResult listResourceTemplates(
      @JsonRpcParams PaginatedRequestParams params) {
    var page = paginate(sortedTemplateDescriptors, params);
    return new ListResourceTemplatesResult(page.items(), page.nextCursor());
  }

  @JsonRpcMethod("resources/read")
  public ReadResourceResult readResource(@JsonRpcParams ResourceRequestParams params) {
    String uri = params.uri();

    McpResource exact = resources.get(uri);
    if (exact != null) {
      return exact.read();
    }

    for (var entry : templates.entrySet()) {
      if (entry.getKey().matches(uri)) {
        return entry.getValue().read(entry.getKey().match(uri));
      }
    }

    throw new JsonRpcException(
        JsonRpcProtocol.INVALID_PARAMS, String.format("Resource not found: %s", uri));
  }

  @JsonRpcMethod("resources/subscribe")
  public EmptyResult subscribe(@JsonRpcParams ResourceRequestParams params) {
    subscriptions.add(params.uri());
    return EmptyResult.INSTANCE;
  }

  @JsonRpcMethod("resources/unsubscribe")
  public EmptyResult unsubscribe(@JsonRpcParams ResourceRequestParams params) {
    subscriptions.remove(params.uri());
    return EmptyResult.INSTANCE;
  }

  public boolean isEmpty() {
    return resources.isEmpty() && templates.isEmpty();
  }

  public boolean isSubscribed(String uri) {
    return subscriptions.contains(uri);
  }

  private <T> Page<T> paginate(List<T> all, PaginatedRequestParams params) {
    String cursor = params == null ? null : params.cursor();
    int offset = Math.clamp(decodeCursor(cursor), 0, all.size());
    int end = Math.min(offset + pageSize, all.size());
    List<T> page = List.copyOf(all.subList(offset, end));
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return new Page<>(page, nextCursor);
  }

  private static String encodeCursor(int offset) {
    return Base64.getEncoder().encodeToString(ByteBuffer.allocate(4).putInt(offset).array());
  }

  private static int decodeCursor(String cursor) {
    if (cursor == null) {
      return 0;
    }
    try {
      return ByteBuffer.wrap(Base64.getDecoder().decode(cursor)).getInt();
    } catch (Exception _) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
  }

  private record Page<T>(List<T> items, String nextCursor) {}
}
