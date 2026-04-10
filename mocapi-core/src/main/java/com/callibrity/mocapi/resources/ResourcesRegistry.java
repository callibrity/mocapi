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
package com.callibrity.mocapi.resources;

import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.web.util.UriTemplate;

public class ResourcesRegistry {

  private final Map<String, McpResource> resources;
  private final Map<UriTemplate, McpResourceTemplate> templates;
  private final int pageSize;
  private final Set<String> subscribedUris = ConcurrentHashMap.newKeySet();

  public ResourcesRegistry(
      List<McpResource> resources, List<McpResourceTemplate> templates, int pageSize) {
    this.resources =
        resources.stream().collect(Collectors.toMap(McpResource::uri, r -> r, (a, b) -> a));
    this.templates =
        templates.stream()
            .collect(
                Collectors.toMap(
                    t -> new UriTemplate(t.uriTemplate()),
                    t -> t,
                    (a, b) -> a,
                    LinkedHashMap::new));
    this.pageSize = pageSize;
  }

  public boolean isEmpty() {
    return resources.isEmpty() && templates.isEmpty();
  }

  public ListResourcesResponse listResources(String cursor) {
    List<McpResourceDescriptor> allResources =
        resources.values().stream()
            .map(r -> new McpResourceDescriptor(r.uri(), r.name(), r.description(), r.mimeType()))
            .sorted(Comparator.comparing(McpResourceDescriptor::uri))
            .toList();
    return paginateResources(allResources, cursor);
  }

  public ListResourceTemplatesResponse listResourceTemplates(String cursor) {
    List<McpResourceTemplateDescriptor> allTemplates =
        templates.values().stream()
            .map(
                t ->
                    new McpResourceTemplateDescriptor(
                        t.uriTemplate(), t.name(), t.description(), t.mimeType()))
            .sorted(Comparator.comparing(McpResourceTemplateDescriptor::uriTemplate))
            .toList();
    return paginateTemplates(allTemplates, cursor);
  }

  public ReadResourceResponse readResource(String uri) {
    McpResource exact = resources.get(uri);
    if (exact != null) {
      return exact.read();
    }

    for (var entry : templates.entrySet()) {
      if (entry.getKey().matches(uri)) {
        Map<String, String> vars = entry.getKey().match(uri);
        return entry.getValue().read(vars);
      }
    }

    throw new JsonRpcException(
        JsonRpcProtocol.INVALID_PARAMS, String.format("Resource not found: %s", uri));
  }

  public void subscribe(String uri) {
    subscribedUris.add(uri);
  }

  public void unsubscribe(String uri) {
    subscribedUris.remove(uri);
  }

  public boolean isSubscribed(String uri) {
    return subscribedUris.contains(uri);
  }

  private ListResourcesResponse paginateResources(List<McpResourceDescriptor> all, String cursor) {
    int offset = decodeCursor(cursor);
    if (offset < 0 || offset > all.size()) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
    int end = Math.min(offset + pageSize, all.size());
    List<McpResourceDescriptor> page = all.subList(offset, end);
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return new ListResourcesResponse(new ArrayList<>(page), nextCursor);
  }

  private ListResourceTemplatesResponse paginateTemplates(
      List<McpResourceTemplateDescriptor> all, String cursor) {
    int offset = decodeCursor(cursor);
    if (offset < 0 || offset > all.size()) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
    int end = Math.min(offset + pageSize, all.size());
    List<McpResourceTemplateDescriptor> page = all.subList(offset, end);
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return new ListResourceTemplatesResponse(new ArrayList<>(page), nextCursor);
  }

  static String encodeCursor(int offset) {
    return Base64.getEncoder()
        .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
  }

  static int decodeCursor(String cursor) {
    if (cursor == null) {
      return 0;
    }
    try {
      return Integer.parseInt(
          new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException _) {
      return -1;
    }
  }
}
