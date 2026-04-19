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
package com.callibrity.mocapi.api.sampling;

import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.IncludeContext;
import com.callibrity.mocapi.model.ModelPreferences;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.model.ToolChoice;
import java.util.List;

/**
 * Fluent configuration surface for a sampling {@code createMessage} request. Tool authors receive
 * an instance from {@link
 * com.callibrity.mocapi.api.tools.McpToolContext#sample(java.util.function.Consumer)}; the runtime
 * constructs the concrete builder behind the scenes and calls {@code build()} itself once the
 * customizer returns, so callers only ever see this config-only view.
 *
 * <p>Only {@code messages} and {@code maxTokens} are required per the MCP spec. {@code maxTokens}
 * defaults to {@value #DEFAULT_MAX_TOKENS}; at least one message must be added or {@code build()}
 * will throw.
 */
public interface CreateMessageRequestConfig {

  /**
   * Default {@code maxTokens} if the caller doesn't set one. Generous enough for most tool-
   * triggered samples without being wasteful.
   */
  int DEFAULT_MAX_TOKENS = 1024;

  // --- Messages ---------------------------------------------------------------------------------

  /** Appends a user-role message with plain text content. */
  CreateMessageRequestConfig userMessage(String text);

  /** Appends an assistant-role message with plain text content. Useful for few-shot prompting. */
  CreateMessageRequestConfig assistantMessage(String text);

  /** Appends a message with the given role and content block (text, image, audio, etc.). */
  CreateMessageRequestConfig message(Role role, ContentBlock content);

  // --- Scalars ----------------------------------------------------------------------------------

  /** System prompt — client decides whether/how to honor it. */
  CreateMessageRequestConfig systemPrompt(String systemPrompt);

  /** Override the default maxTokens of {@value #DEFAULT_MAX_TOKENS}. */
  CreateMessageRequestConfig maxTokens(int maxTokens);

  /** Sampling temperature hint. */
  CreateMessageRequestConfig temperature(double temperature);

  /**
   * Include-context hint per MCP spec: {@code NONE}, {@code THIS_SERVER}, or {@code ALL_SERVERS}.
   */
  CreateMessageRequestConfig includeContext(IncludeContext includeContext);

  /** Stop sequences hint. Replaces any previously set list. */
  CreateMessageRequestConfig stopSequences(String... stopSequences);

  // --- Model preferences ------------------------------------------------------------------------

  /** Appends a model hint by name (e.g. {@code "claude-3-sonnet"}). Multiple calls accumulate. */
  CreateMessageRequestConfig preferModel(String modelNameHint);

  /** Cost priority 0.0–1.0. Higher = the client should prefer cheaper models. */
  CreateMessageRequestConfig costPriority(double costPriority);

  /** Speed priority 0.0–1.0. Higher = the client should prefer faster models. */
  CreateMessageRequestConfig speedPriority(double speedPriority);

  /** Intelligence priority 0.0–1.0. Higher = the client should prefer more capable models. */
  CreateMessageRequestConfig intelligencePriority(double intelligencePriority);

  /**
   * Supply a fully-constructed {@link ModelPreferences} directly. Overrides anything set via {@link
   * #preferModel}, {@link #costPriority}, {@link #speedPriority}, or {@link #intelligencePriority}.
   */
  CreateMessageRequestConfig modelPreferences(ModelPreferences modelPreferences);

  // --- Tools ------------------------------------------------------------------------------------

  /** Append a tool definition the server wants to expose to the LLM during this sample. */
  CreateMessageRequestConfig tool(Tool tool);

  /**
   * Append a tool by name, looked up from this server's registered tools.
   *
   * @throws IllegalArgumentException if no tool with the given name is registered
   */
  CreateMessageRequestConfig tool(String name);

  /** Append all tools from the given list. */
  CreateMessageRequestConfig tools(List<Tool> tools);

  /** Append every tool registered on this server. */
  CreateMessageRequestConfig allServerTools();

  /** Set the tool-choice hint for this sample. */
  CreateMessageRequestConfig toolChoice(ToolChoice toolChoice);
}
