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
package com.callibrity.mocapi.api.resources;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a templated MCP resource. Method parameters named to match placeholders in the
 * {@link #uriTemplate()} receive the extracted values (converted via a Spring {@code
 * ConversionService}). A {@code Map<String, String>} parameter receives the entire path-variable
 * map. Method must return a {@code ReadResourceResult}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ResourceTemplateMethod {

  /** The RFC 6570 URI template (required). */
  String uriTemplate();

  /** The resource template name. If not specified, a human-readable version will be generated. */
  String name() default "";

  /** A description of the resource template. If not specified, the name will be used. */
  String description() default "";

  /** The MIME type of the resource content. Optional. */
  String mimeType() default "";
}
