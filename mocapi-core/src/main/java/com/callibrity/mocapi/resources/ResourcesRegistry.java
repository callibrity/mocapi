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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ResourcesRegistry {

  private final List<McpResourceProvider> providers;
  private final int pageSize;
  private final Set<String> subscribedUris = ConcurrentHashMap.newKeySet();

  public ResourcesRegistry(List<McpResourceProvider> providers, int pageSize) {
    this.providers = List.copyOf(providers);
    this.pageSize = pageSize;
  }

  public ListResourcesResponse listResources(String cursor) {
    List<McpResource> allResources =
        providers.stream()
            .flatMap(p -> p.getResources().stream())
            .sorted(Comparator.comparing(McpResource::uri))
            .toList();
    return paginateResources(allResources, cursor);
  }

  public ListResourceTemplatesResponse listResourceTemplates(String cursor) {
    List<McpResourceTemplate> allTemplates =
        providers.stream()
            .flatMap(p -> p.getResourceTemplates().stream())
            .sorted(Comparator.comparing(McpResourceTemplate::uriTemplate))
            .toList();
    return paginateTemplates(allTemplates, cursor);
  }

  public ReadResourceResponse readResource(String uri) {
    for (McpResourceProvider provider : providers) {
      ReadResourceResponse response = provider.read(uri);
      if (response != null) {
        return response;
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

  private ListResourcesResponse paginateResources(List<McpResource> all, String cursor) {
    int offset = decodeCursor(cursor);
    if (offset < 0 || offset > all.size()) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
    int end = Math.min(offset + pageSize, all.size());
    List<McpResource> page = all.subList(offset, end);
    String nextCursor = end < all.size() ? encodeCursor(end) : null;
    return new ListResourcesResponse(new ArrayList<>(page), nextCursor);
  }

  private ListResourceTemplatesResponse paginateTemplates(
      List<McpResourceTemplate> all, String cursor) {
    int offset = decodeCursor(cursor);
    if (offset < 0 || offset > all.size()) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Invalid cursor");
    }
    int end = Math.min(offset + pageSize, all.size());
    List<McpResourceTemplate> page = all.subList(offset, end);
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
