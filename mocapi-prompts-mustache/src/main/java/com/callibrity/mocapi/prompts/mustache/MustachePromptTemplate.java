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
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.samskivert.mustache.Template;
import java.util.List;
import java.util.Map;

/**
 * JMustache-backed {@link PromptTemplate}. Renders the compiled Mustache template against the
 * supplied argument map and emits a single {@link PromptMessage} with the configured {@link Role}.
 * The optional description is attached verbatim to each {@link GetPromptResult}.
 */
public class MustachePromptTemplate implements PromptTemplate {

  private final Role role;
  private final String description;
  private final Template compiled;

  public MustachePromptTemplate(Role role, String description, Template compiled) {
    this.role = role;
    this.description = description;
    this.compiled = compiled;
  }

  public MustachePromptTemplate(Role role, Template compiled) {
    this(role, null, compiled);
  }

  @Override
  public GetPromptResult render(Map<String, String> args) {
    var rendered = compiled.execute(args == null ? Map.of() : args);
    return new GetPromptResult(
        description, List.of(new PromptMessage(role, new TextContent(rendered, null))));
  }
}
