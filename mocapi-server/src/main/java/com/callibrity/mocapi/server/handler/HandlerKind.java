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
package com.callibrity.mocapi.server.handler;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.tools.McpTool;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Classifies an MCP handler method. Used by cross-cutting modules (audit, logging, observability)
 * as a low-cardinality tag on MDC entries, metric labels, and span attributes. {@link #tag()}
 * yields the stable string form ({@code "tool"}, {@code "prompt"}, {@code "resource"}, {@code
 * "resource_template"}) emitted in those contexts.
 */
public enum HandlerKind {
  TOOL("tool", McpTool.class),
  PROMPT("prompt", McpPrompt.class),
  RESOURCE("resource", McpResource.class),
  RESOURCE_TEMPLATE("resource_template", McpResourceTemplate.class);

  private final String tag;
  private final Class<? extends Annotation> annotation;

  HandlerKind(String tag, Class<? extends Annotation> annotation) {
    this.tag = tag;
    this.annotation = annotation;
  }

  /**
   * Stable string form of this kind — suitable for MDC values, metric labels, and span attribute
   * values. Never {@code null}.
   */
  public String tag() {
    return tag;
  }

  /**
   * Returns the {@link HandlerKind} for the given method based on which MCP handler annotation it
   * carries, or {@code null} if the method is not annotated with any of them.
   */
  public static HandlerKind of(Method method) {
    for (HandlerKind kind : values()) {
      if (method.isAnnotationPresent(kind.annotation)) {
        return kind;
      }
    }
    return null;
  }

  /**
   * Returns the handler's declared name as written on its annotation — tool / prompt {@code name()}
   * or resource {@code uri()} / resource-template {@code uriTemplate()}. Returns an empty string if
   * the annotation's value was left at its default (blank) — callers who treat blank as "unset"
   * should filter accordingly.
   */
  public String nameOf(Method method) {
    return switch (this) {
      case TOOL -> method.getAnnotation(McpTool.class).name();
      case PROMPT -> method.getAnnotation(McpPrompt.class).name();
      case RESOURCE -> method.getAnnotation(McpResource.class).uri();
      case RESOURCE_TEMPLATE -> method.getAnnotation(McpResourceTemplate.class).uriTemplate();
    };
  }
}
