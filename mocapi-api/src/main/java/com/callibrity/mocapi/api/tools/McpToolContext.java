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
package com.callibrity.mocapi.api.tools;

import com.callibrity.mocapi.api.elicitation.RequestedSchemaBuilder;
import com.callibrity.mocapi.api.sampling.CreateMessageRequestConfig;
import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import java.util.function.Consumer;

/**
 * Context available to tool methods that need mid-execution communication with the client. Provides
 * methods for sending progress notifications, log messages, elicitation requests, and sampling
 * requests. Tools return their final result via the method return value — this context is only for
 * mid-execution communication.
 */
public interface McpToolContext {

  ScopedValue<McpToolContext> CURRENT = ScopedValue.newInstance();

  /**
   * Sends a progress notification to the client.
   *
   * @param progress the current progress value
   * @param total the total expected value
   */
  void sendProgress(long progress, long total);

  /**
   * Sends a log notification to the client. Messages below the session's current log level are
   * silently dropped.
   *
   * @param level the log level
   * @param logger the logger name
   * @param message the log message
   */
  void log(LoggingLevel level, String logger, String message);

  /**
   * Returns true if a message logged at {@code level} would be forwarded to the client. Default
   * implementations (test doubles, programmatic callers) permit every level; the runtime
   * implementation consults the bound session's log level.
   *
   * @param level the candidate log level
   * @return whether the level is currently enabled
   */
  default boolean isEnabled(LoggingLevel level) {
    return true;
  }

  /**
   * Returns the name of the handler currently executing (the {@code @McpTool} name, or the prompt /
   * resource name). Implementations that don't know (test doubles, programmatic callers) return
   * {@code "mcp"}.
   *
   * @return the current handler name
   */
  default String handlerName() {
    return "mcp";
  }

  /**
   * Returns an {@link McpLogger} that publishes messages under {@code name}.
   *
   * @param name the logger name
   * @return a logger bound to this context
   */
  default McpLogger logger(String name) {
    return new ContextMcpLogger(this, name);
  }

  /**
   * Returns an {@link McpLogger} named after the handler currently executing (see {@link
   * #handlerName()}).
   *
   * @return a logger named after the current handler
   */
  default McpLogger logger() {
    return logger(handlerName());
  }

  /**
   * Sends an elicitation request to the client and blocks until the client responds.
   *
   * @param params the elicitation request parameters
   * @return the client's elicitation result
   */
  ElicitResult elicit(ElicitRequestFormParams params);

  /**
   * Sends an elicitation request using a fluent schema builder. Example:
   *
   * <pre>{@code
   * ElicitResult result = ctx.elicit("Please enter your details", schema -> schema
   *     .string("name", "Your name")
   *     .string("email", "Email address", s -> s.email())
   *     .integer("age", "Your age", s -> s.optional().min(0).max(150))
   * );
   * }</pre>
   *
   * @param message the message to display to the user
   * @param schemaCustomizer configures the schema via {@link RequestedSchemaBuilder}
   * @return the client's elicitation result
   */
  default ElicitResult elicit(String message, Consumer<RequestedSchemaBuilder> schemaCustomizer) {
    var builder = new RequestedSchemaBuilder();
    schemaCustomizer.accept(builder);
    var params = new ElicitRequestFormParams("form", message, builder.build(), null, null);
    return elicit(params);
  }

  /**
   * Sends a sampling (createMessage) request to the client and blocks until the client responds.
   *
   * @param params the create-message request parameters
   * @return the client's sampling result
   */
  CreateMessageResult sample(CreateMessageRequestParams params);

  /**
   * Shortcut for a simple one-shot sampling request with a single user message. Uses the default
   * {@code maxTokens} ({@value CreateMessageRequestConfig#DEFAULT_MAX_TOKENS}) and no other
   * overrides — the client picks the model, temperature, etc.
   *
   * <pre>{@code
   * CreateMessageResult result = ctx.sample("Summarize the above in one sentence.");
   * }</pre>
   *
   * @param userMessage the user-role text prompt
   * @return the client's sampling result
   */
  default CreateMessageResult sample(String userMessage) {
    return sample(b -> b.userMessage(userMessage));
  }

  /**
   * Shortcut for {@link #sample(String)} that returns only the assistant's text content — by far
   * the most common use of a sampling result. Equivalent to {@code sample(prompt).text()}. Returns
   * {@code null} if the assistant responded with a non-text content block.
   *
   * <pre>{@code
   * String summary = ctx.sampleText("Summarize the above in one sentence.");
   * }</pre>
   */
  default String sampleText(String userMessage) {
    return sample(userMessage).text();
  }

  /**
   * Same as {@link #sampleText(String)} but takes a fluent {@link CreateMessageRequestConfig}
   * customizer.
   */
  default String sampleText(Consumer<CreateMessageRequestConfig> customizer) {
    return sample(customizer).text();
  }

  /**
   * Sends a sampling request built via a fluent {@link CreateMessageRequestConfig}. Only {@code
   * messages} and {@code maxTokens} are required per the MCP spec; {@code maxTokens} defaults to
   * {@value CreateMessageRequestConfig#DEFAULT_MAX_TOKENS}. Example:
   *
   * <pre>{@code
   * CreateMessageResult result = ctx.sample(b -> b
   *     .userMessage("Explain this code")
   *     .systemPrompt("You are a code reviewer.")
   *     .maxTokens(500)
   *     .intelligencePriority(0.8)
   *     .preferModel("claude-3-sonnet"));
   * }</pre>
   *
   * <p>Implementations provide the concrete builder (the runtime's server-aware one wires up the
   * tool registry so {@link CreateMessageRequestConfig#tool(String)} and {@link
   * CreateMessageRequestConfig#allServerTools()} work).
   *
   * @param customizer configures the request via {@link CreateMessageRequestConfig}
   * @return the client's sampling result
   */
  CreateMessageResult sample(Consumer<CreateMessageRequestConfig> customizer);
}
