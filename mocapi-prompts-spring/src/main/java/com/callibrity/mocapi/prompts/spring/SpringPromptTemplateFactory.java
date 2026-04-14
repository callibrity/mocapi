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
package com.callibrity.mocapi.prompts.spring;

import com.callibrity.mocapi.api.prompts.template.PromptTemplate;
import com.callibrity.mocapi.api.prompts.template.PromptTemplateFactory;
import com.callibrity.mocapi.model.Role;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * {@link PromptTemplateFactory} backed by Spring's {@link PropertyPlaceholderHelper}. Templates use
 * {@code ${name}} placeholders; {@code ${name:default}} supplies a fallback when the argument is
 * missing. Unknown placeholders are left in place by default — override by passing a {@code
 * PropertyPlaceholderHelper} constructed with {@code ignoreUnresolvablePlaceholders=false}.
 */
public class SpringPromptTemplateFactory implements PromptTemplateFactory {

  private static final PropertyPlaceholderHelper DEFAULT_HELPER =
      new PropertyPlaceholderHelper("${", "}", ":", '\\', true);

  private final PropertyPlaceholderHelper helper;

  public SpringPromptTemplateFactory() {
    this(DEFAULT_HELPER);
  }

  public SpringPromptTemplateFactory(PropertyPlaceholderHelper helper) {
    this.helper = helper;
  }

  @Override
  public PromptTemplate create(Role role, String description, String template) {
    return new SpringPromptTemplate(role, description, template, helper);
  }
}
