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

import com.callibrity.mocapi.api.resources.McpResource;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.annotation.AliasFor;

/**
 * Declares a {@code ui://} MCP Apps UI resource. Composes {@link McpResource} (MIME defaulted to
 * {@code text/html;profile=mcp-app}) via meta-annotation, so the method registers as a resource
 * through the standard scan (ADR-0032); {@code csp}/{@code sandbox} drive the resource's {@code
 * _meta.ui}. Like every {@code @McpResource} method, the annotated method must return a {@code
 * ReadResourceResult}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@McpResource(uri = "", mimeType = "text/html;profile=mcp-app")
public @interface McpAppResource {

  @AliasFor(annotation = McpResource.class, attribute = "uri")
  String uri();

  @AliasFor(annotation = McpResource.class, attribute = "name")
  String name() default "";

  Csp csp() default @Csp;

  String[] sandbox() default {};
}
