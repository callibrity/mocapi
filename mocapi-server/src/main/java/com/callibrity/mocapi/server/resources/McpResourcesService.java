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
package com.callibrity.mocapi.server.resources;

import com.callibrity.mocapi.model.ListResourceTemplatesResult;
import com.callibrity.mocapi.model.ListResourcesResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.server.util.Cursors;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.util.UriTemplate;

/** Manages resource registration, lookup, pagination, and JSON-RPC dispatch. */
@JsonRpcService
@lombok.extern.slf4j.Slf4j
public class McpResourcesService {

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Map<String, ReadResourceHandler> resources;
  private final Map<UriTemplate, ReadResourceTemplateHandler> templates;
  private final List<Resource> sortedResourceDescriptors;
  private final List<ResourceTemplate> sortedTemplateDescriptors;
  private final int pageSize;

  public McpResourcesService(
      List<ReadResourceHandler> handlers, List<ReadResourceTemplateHandler> templateHandlers) {
    this(handlers, templateHandlers, DEFAULT_PAGE_SIZE);
  }

  public McpResourcesService(
      List<ReadResourceHandler> handlers,
      List<ReadResourceTemplateHandler> templateHandlers,
      int pageSize) {
    this.resources =
        handlers.stream()
            .collect(
                Collectors.toMap(
                    ReadResourceHandler::uri,
                    h -> h,
                    (a, b) -> {
                      throw new IllegalArgumentException("Duplicate resource URI: " + a.uri());
                    },
                    LinkedHashMap::new));
    this.sortedResourceDescriptors =
        handlers.stream()
            .map(ReadResourceHandler::descriptor)
            .sorted(Comparator.comparing(Resource::uri))
            .toList();

    var templatesByString =
        templateHandlers.stream()
            .collect(
                Collectors.toMap(
                    ReadResourceTemplateHandler::uriTemplate,
                    h -> h,
                    (a, b) -> {
                      throw new IllegalArgumentException(
                          "Duplicate URI template: " + a.uriTemplate());
                    },
                    LinkedHashMap::new));
    this.templates = new LinkedHashMap<>();
    templatesByString.forEach(
        (uriTemplate, handler) -> this.templates.put(new UriTemplate(uriTemplate), handler));
    this.sortedTemplateDescriptors =
        templateHandlers.stream()
            .map(ReadResourceTemplateHandler::descriptor)
            .sorted(Comparator.comparing(ResourceTemplate::uriTemplate))
            .toList();
    this.pageSize = pageSize;
  }

  @JsonRpcMethod(McpMethods.RESOURCES_LIST)
  public ListResourcesResult listResources(@JsonRpcParams PaginatedRequestParams params) {
    return Cursors.paginate(sortedResourceDescriptors, params, pageSize, ListResourcesResult::new);
  }

  @JsonRpcMethod(McpMethods.RESOURCES_TEMPLATES_LIST)
  public ListResourceTemplatesResult listResourceTemplates(
      @JsonRpcParams PaginatedRequestParams params) {
    return Cursors.paginate(
        sortedTemplateDescriptors, params, pageSize, ListResourceTemplatesResult::new);
  }

  @JsonRpcMethod(McpMethods.RESOURCES_READ)
  public ReadResourceResult readResource(@JsonRpcParams ResourceRequestParams params) {
    String uri = params.uri();
    log.debug("Received request to read resource \"{}\"", uri);

    ReadResourceHandler exact = resources.get(uri);
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

  public boolean isEmpty() {
    return resources.isEmpty() && templates.isEmpty();
  }

  /** Returns every registered resource descriptor, sorted by URI. */
  public List<Resource> allResourceDescriptors() {
    return sortedResourceDescriptors;
  }

  /** Returns every registered resource-template descriptor, sorted by URI template. */
  public List<ResourceTemplate> allResourceTemplateDescriptors() {
    return sortedTemplateDescriptors;
  }
}
