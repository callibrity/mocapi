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
package com.callibrity.mocapi.api.resources;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a fixed-URI MCP resource. The method may return a {@code ReadResourceResult}
 * (full control) or a convenience payload that mocapi wraps for you: a {@code String}/{@code
 * CharSequence} (text), a {@code byte[]}/{@code ByteBuffer} (blob), or a {@code
 * org.springframework.core.io.Resource} (text or blob per {@link #content()}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Documented
public @interface McpResource {

  /** The resource URI (required). */
  String uri();

  /** The resource name. If not specified, a human-readable version will be generated. */
  String name() default "";

  /** A description of the resource. If not specified, the name will be used. */
  String description() default "";

  /** The MIME type of the resource content. Optional. */
  String mimeType() default "";

  /**
   * For a {@code org.springframework.core.io.Resource} return, whether its bytes are text or blob.
   * Ignored for the other return types, which decide for themselves. Defaults to {@link
   * ResourceContent#AUTO}, which infers from {@link #mimeType()}.
   */
  ResourceContent content() default ResourceContent.AUTO;
}
