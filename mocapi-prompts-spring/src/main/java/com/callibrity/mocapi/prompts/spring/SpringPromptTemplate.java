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
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.List;
import java.util.Map;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * {@link PromptTemplate} backed by Spring's {@link PropertyPlaceholderHelper}. Substitutes {@code
 * ${name}} placeholders from the supplied argument map and supports default values via {@code
 * ${name:fallback}}. No sections, loops, or conditionals — pure substitution.
 */
public class SpringPromptTemplate implements PromptTemplate {

  private final Role role;
  private final String description;
  private final String template;
  private final PropertyPlaceholderHelper helper;

  public SpringPromptTemplate(
      Role role, String description, String template, PropertyPlaceholderHelper helper) {
    this.role = role;
    this.description = description;
    this.template = template;
    this.helper = helper;
  }

  public SpringPromptTemplate(Role role, String template, PropertyPlaceholderHelper helper) {
    this(role, null, template, helper);
  }

  @Override
  public GetPromptResult render(Map<String, String> args) {
    Map<String, String> safeArgs = args == null ? Map.of() : args;
    String rendered = helper.replacePlaceholders(template, safeArgs::get);
    return new GetPromptResult(
        description, List.of(new PromptMessage(role, new TextContent(rendered, null))));
  }
}
