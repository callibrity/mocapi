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
package com.callibrity.mocapi.apps;

import com.callibrity.mocapi.api.resources.ResourceContent;
import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.server.autoconfigure.HandlerMethodsCache;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ResourceContributor;
import com.callibrity.mocapi.server.resources.ResourceResults;
import com.callibrity.mocapi.server.util.AnnotationStrings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Serve-mode {@link ResourceContributor} for MCP Apps (ADR-0036): for every tool whose
 * {@code @McpUi} sets a non-blank {@code resource}, it contributes a public, reader-only {@code
 * text/html;profile=mcp-app} resource at the {@code @McpUi} URI, serving the bundle resolved once
 * at startup via {@link ResourceLoader}. It plugs into the generic ADR-0035 contributor seam; no
 * Apps concept reaches the core resources line.
 *
 * <p>The URI is logical and author-controlled; the served location is the fixed, author-specified
 * {@code resource} string — never request input. One handler is registered per URI even when
 * several tools link the same UI; two tools naming the same URI with different locations, or a
 * missing bundle, fail the boot.
 */
public final class AppUiResourceContributor implements ResourceContributor {

  private static final Logger log = LoggerFactory.getLogger(AppUiResourceContributor.class);
  private static final String UI_MIME_TYPE = "text/html;profile=mcp-app";

  private final List<ReadResourceHandler> resources;

  public AppUiResourceContributor(
      HandlerMethodsCache cache,
      ResourceLoader resourceLoader,
      ObjectMapper mapper,
      UnaryOperator<String> resolver) {
    if (cache == null) {
      this.resources = List.of();
      return;
    }
    Map<String, String> locationByUri = new LinkedHashMap<>();
    for (HandlerMethodsCache.BeanMethod bm : cache.forAnnotation(McpTool.class)) {
      McpUi ui = AnnotatedElementUtils.findMergedAnnotation(bm.method(), McpUi.class);
      String resourceLocation =
          ui == null ? null : AnnotationStrings.resolveOrNull(resolver, ui.resource());
      if (ui == null || resourceLocation == null) {
        continue;
      }
      String uri = AnnotationStrings.resolveOrNull(resolver, ui.value());
      String existing = locationByUri.putIfAbsent(uri, resourceLocation);
      if (existing != null && !existing.equals(resourceLocation)) {
        throw new IllegalStateException(
            "MCP Apps: @McpUi(\""
                + uri
                + "\") is served from two different locations: \""
                + existing
                + "\" and \""
                + resourceLocation
                + "\"");
      }
    }
    this.resources =
        locationByUri.entrySet().stream()
            .map(e -> serve(e.getKey(), e.getValue(), resourceLoader, mapper))
            .toList();
  }

  private static ReadResourceHandler serve(
      String uri, String location, ResourceLoader resourceLoader, ObjectMapper mapper) {
    ReadResourceResult bundle =
        ResourceResults.toResult(
            resourceLoader.getResource(location), uri, UI_MIME_TYPE, ResourceContent.TEXT);
    ObjectNode meta = mapper.createObjectNode();
    meta.set("ui", mapper.createObjectNode());
    String name = friendlyName(uri);
    Resource descriptor = new Resource(uri, name, name, UI_MIME_TYPE).withMeta(meta);
    log.info("Registered MCP Apps UI resource: \"{}\" (serving \"{}\")", uri, location);
    return new ReadResourceHandler(descriptor, List.of(), () -> bundle);
  }

  /**
   * A human-readable name derived from the {@code ui://} URI, so serve-mode resources don't list
   * under their raw URI. Strips the scheme, drops a trailing filename segment (one with a dot), and
   * title-cases the remaining leaf on {@code -}/{@code _} — e.g. {@code ui://get-time/mcp-app.html}
   * → {@code "Get Time"}. Falls back to the URI if nothing usable remains.
   */
  static String friendlyName(String uri) {
    String path = uri.replaceFirst("^[^:]+://", "");
    String[] segments = path.split("/");
    int leaf = segments.length - 1;
    if (leaf > 0 && segments[leaf].contains(".")) {
      leaf--;
    }
    StringBuilder name = new StringBuilder();
    for (String word : segments[leaf].split("[-_]+")) {
      if (word.isEmpty()) {
        continue;
      }
      if (!name.isEmpty()) {
        name.append(' ');
      }
      name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return name.isEmpty() ? uri : name.toString();
  }

  @Override
  public List<ReadResourceHandler> resources() {
    return resources;
  }
}
