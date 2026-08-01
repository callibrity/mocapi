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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a tool's results to a {@code ui://} UI resource (MCP Apps) via the tool descriptor's {@code
 * _meta.ui}.
 *
 * <p>By default the {@link #value()} URI must be declared elsewhere on the server (an
 * {@code @McpAppResource} / {@code @McpResource} method with the same URI), and startup fails fast
 * if it is not. Set {@link #resource()} to serve the bundle directly from a fixed
 * classpath/filesystem location — mocapi contributes the {@code ui://} resource for you (ADR-0036),
 * no resource method required.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpUi {
  /** The linked UI resource URI (must start with {@code ui://}). */
  String value();

  /**
   * Optional fixed location of the UI bundle to serve at {@link #value()} — any Spring {@code
   * ResourceLoader} location ({@code classpath:/…}, {@code file:/…}). When set, mocapi contributes
   * a public {@code text/html;profile=mcp-app} resource at {@code value()} serving these bytes,
   * resolved once at startup; a missing location fails the boot. The location is fixed and
   * author-controlled — never derived from client input. Leave blank to declare the resource
   * yourself (the path for guards, observability, custom CSP/sandbox, or generated content).
   */
  String resource() default "";

  /** UI access axis: {@code "model"} and/or {@code "app"}. Default: both. */
  String[] visibility() default {"model", "app"};
}
