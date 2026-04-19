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
package com.callibrity.mocapi.examples.prompts;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.prompts.PromptService;
import com.callibrity.mocapi.api.prompts.template.PromptTemplate;
import com.callibrity.mocapi.api.prompts.template.PromptTemplateFactory;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Role;
import java.util.Map;

/**
 * Example prompt that exercises two features at once:
 *
 * <ul>
 *   <li>Enum-typed argument completion — MCP clients asking {@code completion/complete} for the
 *       {@code detail} argument get {@code [BRIEF, STANDARD, DETAILED]}, prefix-filtered by
 *       whatever the user has typed.
 *   <li>{@link PromptTemplateFactory} rendering — the prompt body is a Spring-placeholder template
 *       compiled once at construction and rendered per invocation, rather than built with String
 *       concatenation.
 * </ul>
 */
@PromptService
public class SummarizePrompt {

  public enum Detail {
    BRIEF,
    STANDARD,
    DETAILED
  }

  private static final String TEMPLATE =
      "Please summarize the following text at ${detail} detail:\n\n${text}";

  private final PromptTemplate compiledTemplate;

  public SummarizePrompt(PromptTemplateFactory factory) {
    this.compiledTemplate = factory.create(Role.USER, "Summarization instruction", TEMPLATE);
  }

  @McpPrompt(name = "summarize", description = "Summarize text at a specified detail level")
  public GetPromptResult summarize(String text, Detail detail) {
    Detail level = detail == null ? Detail.STANDARD : detail;
    return compiledTemplate.render(Map.of("text", text, "detail", level.name()));
  }
}
