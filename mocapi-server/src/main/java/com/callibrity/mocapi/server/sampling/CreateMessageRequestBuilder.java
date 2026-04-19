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
package com.callibrity.mocapi.server.sampling;

import com.callibrity.mocapi.api.sampling.CreateMessageRequestConfig;
import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.IncludeContext;
import com.callibrity.mocapi.model.ModelHint;
import com.callibrity.mocapi.model.ModelPreferences;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.SamplingMessage;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.model.ToolChoice;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link CreateMessageRequestConfig} that uses {@link McpToolsService} to
 * resolve {@link #tool(String)} lookups and {@link #allServerTools()} enumeration.
 */
public final class CreateMessageRequestBuilder implements CreateMessageRequestConfig {

  private final McpToolsService toolsService;
  private final List<SamplingMessage> messages = new ArrayList<>();
  private final List<ModelHint> hints = new ArrayList<>();
  private final List<Tool> tools = new ArrayList<>();
  private ModelPreferences modelPreferences;
  private String systemPrompt;
  private IncludeContext includeContext;
  private Double temperature;
  private int maxTokens = DEFAULT_MAX_TOKENS;
  private List<String> stopSequences;
  private Double costPriority;
  private Double speedPriority;
  private Double intelligencePriority;
  private ToolChoice toolChoice;

  public CreateMessageRequestBuilder(McpToolsService toolsService) {
    this.toolsService = toolsService;
  }

  @Override
  public CreateMessageRequestBuilder userMessage(String text) {
    return message(Role.USER, new TextContent(text, null));
  }

  @Override
  public CreateMessageRequestBuilder assistantMessage(String text) {
    return message(Role.ASSISTANT, new TextContent(text, null));
  }

  @Override
  public CreateMessageRequestBuilder message(Role role, ContentBlock content) {
    messages.add(new SamplingMessage(role, content));
    return this;
  }

  @Override
  public CreateMessageRequestBuilder userMessages(String... texts) {
    for (String text : texts) {
      userMessage(text);
    }
    return this;
  }

  @Override
  public CreateMessageRequestBuilder assistantMessages(String... texts) {
    for (String text : texts) {
      assistantMessage(text);
    }
    return this;
  }

  @Override
  public CreateMessageRequestBuilder systemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder maxTokens(int maxTokens) {
    this.maxTokens = maxTokens;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder temperature(double temperature) {
    this.temperature = temperature;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder includeContext(IncludeContext includeContext) {
    this.includeContext = includeContext;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder stopSequences(String... stopSequences) {
    this.stopSequences = List.of(stopSequences);
    return this;
  }

  @Override
  public CreateMessageRequestBuilder preferModel(String modelNameHint) {
    hints.add(new ModelHint(modelNameHint));
    return this;
  }

  @Override
  public CreateMessageRequestBuilder preferModels(String... modelNameHints) {
    for (String hint : modelNameHints) {
      preferModel(hint);
    }
    return this;
  }

  @Override
  public CreateMessageRequestBuilder costPriority(double costPriority) {
    this.costPriority = costPriority;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder speedPriority(double speedPriority) {
    this.speedPriority = speedPriority;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder intelligencePriority(double intelligencePriority) {
    this.intelligencePriority = intelligencePriority;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder modelPreferences(ModelPreferences modelPreferences) {
    this.modelPreferences = modelPreferences;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder tool(Tool tool) {
    tools.add(tool);
    return this;
  }

  @Override
  public CreateMessageRequestBuilder tool(String name) {
    Tool resolved = toolsService.findToolDescriptor(name);
    if (resolved == null) {
      throw new IllegalArgumentException("No tool registered with name: " + name);
    }
    tools.add(resolved);
    return this;
  }

  @Override
  public CreateMessageRequestBuilder tools(String... names) {
    for (String name : names) {
      tool(name);
    }
    return this;
  }

  @Override
  public CreateMessageRequestBuilder allServerTools() {
    tools.addAll(toolsService.allToolDescriptors());
    return this;
  }

  @Override
  public CreateMessageRequestBuilder toolChoice(ToolChoice toolChoice) {
    this.toolChoice = toolChoice;
    return this;
  }

  @Override
  public CreateMessageRequestBuilder autoToolChoice() {
    return toolChoice(ToolChoice.auto());
  }

  @Override
  public CreateMessageRequestBuilder noneToolChoice() {
    return toolChoice(ToolChoice.none());
  }

  @Override
  public CreateMessageRequestBuilder mustUseTool(String name) {
    return toolChoice(ToolChoice.specific(name));
  }

  public CreateMessageRequestParams build() {
    if (messages.isEmpty()) {
      throw new IllegalStateException(
          "At least one message is required — call userMessage(...) before build()");
    }
    return new CreateMessageRequestParams(
        List.copyOf(messages),
        resolvedModelPreferences(),
        systemPrompt,
        includeContext,
        temperature,
        maxTokens,
        stopSequences,
        null,
        tools.isEmpty() ? null : List.copyOf(tools),
        toolChoice,
        null,
        null);
  }

  private ModelPreferences resolvedModelPreferences() {
    if (modelPreferences != null) {
      return modelPreferences;
    }
    boolean anyPriority =
        costPriority != null || speedPriority != null || intelligencePriority != null;
    if (hints.isEmpty() && !anyPriority) {
      return null;
    }
    return new ModelPreferences(
        hints.isEmpty() ? null : List.copyOf(hints),
        costPriority,
        speedPriority,
        intelligencePriority);
  }
}
