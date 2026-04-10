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

import com.callibrity.mocapi.model.ListResourceTemplatesResult;
import com.callibrity.mocapi.model.ListResourcesResult;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.util.Cursors;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.util.UriTemplate;

public class ResourcesRegistry {

  private final Map<String, McpResource> resources;
  private final Map<UriTemplate, McpResourceTemplate> templates;
  private final int pageSize;
  private final List<Resource> sortedResourceDescriptors;
  private final List<ResourceTemplate> sortedTemplateDescriptors;

  public ResourcesRegistry(
      List<McpResource> resources, List<McpResourceTemplate> templates, int pageSize) {
    this.resources =
        resources.stream().collect(Collectors.toMap(r -> r.descriptor().uri(), r -> r));
    this.sortedResourceDescriptors =
        resources.stream()
            .map(McpResource::descriptor)
            .sorted(Comparator.comparing(Resource::uri))
            .toList();
    this.templates =
        templates.stream()
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
        templates.stream()
            .map(McpResourceTemplate::descriptor)
            .sorted(Comparator.comparing(ResourceTemplate::uriTemplate))
            .toList();
    this.pageSize = pageSize;
  }

  public boolean isEmpty() {
    return resources.isEmpty() && templates.isEmpty();
  }

  public ListResourcesResult listResources(String cursor) {
    var page = Cursors.paginate(sortedResourceDescriptors, cursor, pageSize);
    return new ListResourcesResult(page.items(), page.nextCursor());
  }

  public ListResourceTemplatesResult listResourceTemplates(String cursor) {
    var page = Cursors.paginate(sortedTemplateDescriptors, cursor, pageSize);
    return new ListResourceTemplatesResult(page.items(), page.nextCursor());
  }

  public ReadResourceResult readResource(String uri) {
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
}
