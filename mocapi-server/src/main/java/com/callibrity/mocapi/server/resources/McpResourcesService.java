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
import com.callibrity.mocapi.server.guards.Guards;
import com.callibrity.mocapi.server.util.Cursors;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

/** Manages resource registration, lookup, pagination, and JSON-RPC dispatch. */
public class McpResourcesService {

  public static final int DEFAULT_PAGE_SIZE = 50;

  private final Logger log = LoggerFactory.getLogger(McpResourcesService.class);
  private final Map<String, ReadResourceHandler> resources;
  private final Map<UriTemplate, ReadResourceTemplateHandler> templates;
  private final List<ReadResourceHandler> sortedResources;
  private final List<ReadResourceTemplateHandler> sortedTemplates;
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
    this.sortedResources =
        handlers.stream().sorted(Comparator.comparing(ReadResourceHandler::uri)).toList();

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
    this.sortedTemplates =
        templateHandlers.stream()
            .sorted(Comparator.comparing(ReadResourceTemplateHandler::uriTemplate))
            .toList();
    this.pageSize = pageSize;
  }

  @JsonRpcMethod(McpMethods.RESOURCES_LIST)
  public ListResourcesResult listResources(@JsonRpcParams PaginatedRequestParams params) {
    List<Resource> visible =
        sortedResources.stream()
            .filter(h -> Guards.allows(h.guards()))
            .map(ReadResourceHandler::descriptor)
            .toList();
    return Cursors.paginate(visible, params, pageSize, ListResourcesResult::new);
  }

  @JsonRpcMethod(McpMethods.RESOURCES_TEMPLATES_LIST)
  public ListResourceTemplatesResult listResourceTemplates(
      @JsonRpcParams PaginatedRequestParams params) {
    List<ResourceTemplate> visible =
        sortedTemplates.stream()
            .filter(h -> Guards.allows(h.guards()))
            .map(ReadResourceTemplateHandler::descriptor)
            .toList();
    return Cursors.paginate(visible, params, pageSize, ListResourceTemplatesResult::new);
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
        ReadResourceTemplateHandler handler = entry.getValue();
        return handler.read(entry.getKey().match(uri));
      }
    }

    throw new JsonRpcException(
        JsonRpcProtocol.INVALID_PARAMS, String.format("Resource not found: %s", uri));
  }

  public boolean isEmpty() {
    return resources.isEmpty() && templates.isEmpty();
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
