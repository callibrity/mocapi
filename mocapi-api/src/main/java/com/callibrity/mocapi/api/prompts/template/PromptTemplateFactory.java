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
package com.callibrity.mocapi.api.prompts.template;

import com.callibrity.mocapi.model.Role;

/**
 * Compiles raw template sources into reusable {@link PromptTemplate} instances. Each engine
 * integration (Mustache, Handlebars, etc.) supplies an implementation; applications inject a single
 * {@code PromptTemplateFactory} bean and call {@code create(...)} once per template at bean
 * construction time.
 *
 * <p>The caller is responsible for loading the template source text from wherever it lives
 * (classpath, filesystem, database, inline). This keeps the API dependency-free; see the engine
 * integration modules for Spring Boot auto-configuration.
 */
public interface PromptTemplateFactory {

  /**
   * Compiles the given template source and binds it to the supplied role and description. The
   * description is included verbatim in each {@link com.callibrity.mocapi.model.GetPromptResult}
   * produced by the returned template.
   *
   * @param role the role applied to the rendered message
   * @param description optional description exposed on the rendered result; may be {@code null}
   * @param template the raw template source in the engine's native syntax
   * @return a compiled {@link PromptTemplate} ready to render
   * @throws IllegalArgumentException if the template fails to compile
   */
  PromptTemplate create(Role role, String description, String template);

  /** Convenience overload equivalent to {@code create(role, null, template)}. */
  default PromptTemplate create(Role role, String template) {
    return create(role, null, template);
  }
}
