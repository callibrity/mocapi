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
import java.lang.reflect.Method;
import java.util.List;
import org.springframework.core.annotation.AnnotatedElementUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes a UI resource's {@code _meta.ui} (CSP/sandbox) from an {@link McpAppResource}, when
 * present (ADR-0039).
 */
public class AppsResourceDescriptorCustomizer implements ReadResourceHandlerCustomizer {

  private final ObjectMapper mapper;

  public AppsResourceDescriptorCustomizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void customize(ReadResourceHandlerConfig config) {
    Method method = config.method();
    McpAppResource app = AnnotatedElementUtils.findMergedAnnotation(method, McpAppResource.class);
    if (app == null) {
      return;
    }
    UiResourceMeta uiMeta = new UiResourceMeta(csp(app.csp()), listOrNull(app.sandbox()));
    Resource descriptor = config.descriptor();
    ObjectNode meta = descriptor.meta() != null ? descriptor.meta() : mapper.createObjectNode();
    meta.set("ui", mapper.valueToTree(uiMeta));
    config.descriptor(descriptor.withMeta(meta));
  }

  private McpUiResourceCsp csp(Csp csp) {
    McpUiResourceCsp result =
        new McpUiResourceCsp(
            listOrNull(csp.connect()),
            listOrNull(csp.resource()),
            listOrNull(csp.frame()),
            listOrNull(csp.baseUri()));
    boolean empty =
        result.connectDomains() == null
            && result.resourceDomains() == null
            && result.frameDomains() == null
            && result.baseUriDomains() == null;
    return empty ? null : result;
  }

  private static List<String> listOrNull(String[] values) {
    return values.length == 0 ? null : List.of(values);
  }
}
