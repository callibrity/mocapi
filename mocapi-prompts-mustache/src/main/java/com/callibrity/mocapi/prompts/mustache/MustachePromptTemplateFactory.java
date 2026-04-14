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
package com.callibrity.mocapi.prompts.mustache;

import com.callibrity.mocapi.api.prompts.template.PromptTemplate;
import com.callibrity.mocapi.api.prompts.template.PromptTemplateFactory;
import com.callibrity.mocapi.model.Role;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;

/**
 * {@link PromptTemplateFactory} backed by <a href="https://github.com/samskivert/jmustache">
 * JMustache</a>. The default compiler is configured for prompt text: rendered output is consumed by
 * an LLM (never by a browser), so the identity escaper is used rather than JMustache's HTML escaper
 * — HTML escaping would corrupt characters like {@code &lt;}, {@code &gt;}, and {@code &amp;} that
 * legitimately appear in prompts. Missing variables render as empty strings instead of throwing.
 */
public class MustachePromptTemplateFactory implements PromptTemplateFactory {

  private final Mustache.Compiler compiler;

  public MustachePromptTemplateFactory() {
    this(promptTextCompiler());
  }

  public MustachePromptTemplateFactory(Mustache.Compiler compiler) {
    this.compiler = compiler;
  }

  /**
   * Returns a compiler configured for rendering prompt text: identity escaper (no HTML escaping,
   * since the output never reaches a browser) and empty string as the default for missing values.
   */
  public static Mustache.Compiler promptTextCompiler() {
    return Mustache.compiler().withEscaper(Escapers.NONE).defaultValue("");
  }

  @Override
  public PromptTemplate create(Role role, String description, String template) {
    try {
      return new MustachePromptTemplate(role, description, compiler.compile(template));
    } catch (MustacheException e) {
      throw new IllegalArgumentException(
          "Failed to compile Mustache template: " + e.getMessage(), e);
    }
  }
}
