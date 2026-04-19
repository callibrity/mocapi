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
package com.callibrity.mocapi.api.handlers;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.tools.McpTool;
import java.lang.reflect.Method;

/**
 * Shared annotation-introspection helpers for cross-cutting observability interceptors (logging,
 * metrics, tracing). Returns stable string tags suitable for log fields, metric labels, and span
 * attributes.
 */
public final class HandlerKinds {

  /** Kind tag for {@link McpTool}-annotated handlers. */
  public static final String KIND_TOOL = "tool";

  /** Kind tag for {@link McpPrompt}-annotated handlers. */
  public static final String KIND_PROMPT = "prompt";

  /** Kind tag for {@link McpResource}-annotated handlers. */
  public static final String KIND_RESOURCE = "resource";

  /** Kind tag for {@link McpResourceTemplate}-annotated handlers. */
  public static final String KIND_RESOURCE_TEMPLATE = "resource_template";

  private HandlerKinds() {}

  /**
   * Returns the kind tag for the handler method, or {@code null} if {@code method} is not annotated
   * with any of the MCP handler annotations.
   */
  public static String kindOf(Method method) {
    if (method.isAnnotationPresent(McpTool.class)) return KIND_TOOL;
    if (method.isAnnotationPresent(McpPrompt.class)) return KIND_PROMPT;
    if (method.isAnnotationPresent(McpResource.class)) return KIND_RESOURCE;
    if (method.isAnnotationPresent(McpResourceTemplate.class)) return KIND_RESOURCE_TEMPLATE;
    return null;
  }

  /**
   * Returns the handler's declared name as written on its annotation — tool/prompt {@code name()}
   * or resource {@code uri()} / resource-template {@code uriTemplate()}. Returns {@code null} if
   * the method carries no MCP annotation, and returns an empty string if the annotation's value was
   * left at its default (blank) — callers who treat blank as "unset" should filter accordingly.
   */
  public static String nameOf(Method method) {
    if (method.isAnnotationPresent(McpTool.class)) {
      return method.getAnnotation(McpTool.class).name();
    }
    if (method.isAnnotationPresent(McpPrompt.class)) {
      return method.getAnnotation(McpPrompt.class).name();
    }
    if (method.isAnnotationPresent(McpResource.class)) {
      return method.getAnnotation(McpResource.class).uri();
    }
    if (method.isAnnotationPresent(McpResourceTemplate.class)) {
      return method.getAnnotation(McpResourceTemplate.class).uriTemplate();
    }
    return null;
  }
}
