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

import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerConfig;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.util.AnnotationStrings;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.springframework.core.annotation.AnnotatedElementUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes a UI resource's {@code _meta.ui} (CSP/sandbox) from an {@link McpAppResource}, when
 * present (ADR-0039). Each {@code csp}/{@code sandbox} domain list resolves {@code ${...}}
 * placeholders per-element, then falls back to the matching {@link AppsUiDefaults} entry when the
 * resolved list is empty.
 */
public class AppsResourceUiMetaCustomizer implements ReadResourceHandlerCustomizer {

  private final ObjectMapper mapper;
  private final UnaryOperator<String> resolver;
  private final AppsUiDefaults defaults;

  public AppsResourceUiMetaCustomizer(
      ObjectMapper mapper, UnaryOperator<String> resolver, AppsUiDefaults defaults) {
    this.mapper = mapper;
    this.resolver = resolver;
    this.defaults = defaults;
  }

  @Override
  public void customize(ReadResourceHandlerConfig config) {
    Method method = config.method();
    McpAppResource app = AnnotatedElementUtils.findMergedAnnotation(method, McpAppResource.class);
    if (app == null) {
      return;
    }
    UiResourceMeta uiMeta = new UiResourceMeta(csp(app.csp()), sandbox(app.sandbox()));
    Resource descriptor = config.descriptor();
    ObjectNode meta = descriptor.meta() != null ? descriptor.meta() : mapper.createObjectNode();
    meta.set("ui", mapper.valueToTree(uiMeta));
    config.descriptor(descriptor.withMeta(meta));
  }

  private McpUiResourceCsp csp(Csp csp) {
    McpUiResourceCsp result =
        new McpUiResourceCsp(
            resolvedOrDefault(csp.connect(), defaults.cspConnect()),
            resolvedOrDefault(csp.resource(), defaults.cspResource()),
            resolvedOrDefault(csp.frame(), defaults.cspFrame()),
            resolvedOrDefault(csp.baseUri(), defaults.cspBaseUri()));
    boolean empty =
        result.connectDomains() == null
            && result.resourceDomains() == null
            && result.frameDomains() == null
            && result.baseUriDomains() == null;
    return empty ? null : result;
  }

  private List<String> sandbox(String[] values) {
    return resolvedOrDefault(values, defaults.sandbox());
  }

  private List<String> resolvedOrDefault(String[] values, List<String> fallback) {
    List<String> resolved =
        Arrays.stream(values)
            .map(v -> AnnotationStrings.resolveOrNull(resolver, v))
            .filter(Objects::nonNull)
            .toList();
    List<String> effective = resolved.isEmpty() ? fallback : resolved;
    return effective.isEmpty() ? null : effective;
  }
}
