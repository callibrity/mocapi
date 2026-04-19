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
package com.callibrity.mocapi.test;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only implementation of {@link CreateMessageRequestConfig} used by {@link
 * MockMcpToolContext#sample(java.util.function.Consumer)}. Mirrors the server's real builder for
 * every purely-local operation but throws from name-based tool-registry lookups, which cannot be
 * resolved without a running server.
 */
final class TestCreateMessageRequestConfig implements CreateMessageRequestConfig {

  private static final String NO_REGISTRY_MESSAGE =
      "Server tool registry unavailable in MockMcpToolContext — stage a Tool directly with "
          + "tool(Tool) or script the sample response";

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

  @Override
  public TestCreateMessageRequestConfig userMessage(String text) {
    return message(Role.USER, new TextContent(text, null));
  }

  @Override
  public TestCreateMessageRequestConfig assistantMessage(String text) {
    return message(Role.ASSISTANT, new TextContent(text, null));
  }

  @Override
  public TestCreateMessageRequestConfig message(Role role, ContentBlock content) {
    messages.add(new SamplingMessage(role, content));
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig userMessages(String... texts) {
    for (String text : texts) {
      userMessage(text);
    }
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig assistantMessages(String... texts) {
    for (String text : texts) {
      assistantMessage(text);
    }
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig systemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig maxTokens(int maxTokens) {
    this.maxTokens = maxTokens;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig temperature(double temperature) {
    this.temperature = temperature;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig includeContext(IncludeContext includeContext) {
    this.includeContext = includeContext;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig stopSequences(String... stopSequences) {
    this.stopSequences = List.of(stopSequences);
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig preferModel(String modelNameHint) {
    hints.add(new ModelHint(modelNameHint));
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig preferModels(String... modelNameHints) {
    for (String hint : modelNameHints) {
      preferModel(hint);
    }
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig costPriority(double costPriority) {
    this.costPriority = costPriority;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig speedPriority(double speedPriority) {
    this.speedPriority = speedPriority;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig intelligencePriority(double intelligencePriority) {
    this.intelligencePriority = intelligencePriority;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig modelPreferences(ModelPreferences modelPreferences) {
    this.modelPreferences = modelPreferences;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig tool(Tool tool) {
    tools.add(tool);
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig tool(String name) {
    throw new UnsupportedOperationException(NO_REGISTRY_MESSAGE);
  }

  @Override
  public TestCreateMessageRequestConfig tools(String... names) {
    throw new UnsupportedOperationException(NO_REGISTRY_MESSAGE);
  }

  @Override
  public TestCreateMessageRequestConfig allServerTools() {
    throw new UnsupportedOperationException(NO_REGISTRY_MESSAGE);
  }

  @Override
  public TestCreateMessageRequestConfig toolChoice(ToolChoice toolChoice) {
    this.toolChoice = toolChoice;
    return this;
  }

  @Override
  public TestCreateMessageRequestConfig autoToolChoice() {
    return toolChoice(ToolChoice.auto());
  }

  @Override
  public TestCreateMessageRequestConfig noneToolChoice() {
    return toolChoice(ToolChoice.none());
  }

  @Override
  public TestCreateMessageRequestConfig mustUseTool(String name) {
    return toolChoice(ToolChoice.specific(name));
  }

  CreateMessageRequestParams build() {
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
