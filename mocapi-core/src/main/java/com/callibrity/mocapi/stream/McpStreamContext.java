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
package com.callibrity.mocapi.stream;

import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.stream.elicitation.ElicitationResult;
import com.callibrity.mocapi.stream.elicitation.ElicitationSchemaBuilder;
import com.callibrity.mocapi.stream.elicitation.McpElicitationException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.stream.elicitation.McpElicitationTimeoutException;
import java.util.function.Consumer;

/**
 * Handle that MCP handler methods can declare as a parameter to opt into SSE streaming. Provides
 * methods for sending intermediate messages (progress notifications, arbitrary notifications) to
 * the client during a long-running operation, and for eliciting structured input from the client.
 */
public interface McpStreamContext<R> {

  ScopedValue<McpStreamContext<?>> CURRENT = ScopedValue.newInstance();

  /**
   * Publishes the final result on the SSE stream and closes it. The value is wrapped into a {@code
   * CallToolResult} the same way a normal tool return value would be. This is a terminal operation
   * — calling it twice throws {@link IllegalStateException}.
   *
   * @param result the tool result to send
   */
  void sendResult(R result);

  /**
   * Sends a progress notification to the client.
   *
   * @param progress the current progress value
   * @param total the total expected value
   */
  void sendProgress(double progress, double total);

  /**
   * Sends an arbitrary notification to the client.
   *
   * @param method the notification method name
   * @param params the notification parameters (may be null)
   */
  void sendNotification(String method, Object params);

  /**
   * Sends a log notification ({@code notifications/message}) to the client. Messages below the
   * session's current log level threshold are silently dropped.
   *
   * @param level the log level
   * @param logger the logger name (typically the tool name)
   * @param data the log data (message string or structured object)
   */
  void log(LoggingLevel level, String logger, Object data);

  /**
   * Sends a log notification ({@code notifications/message}) to the client using a default logger.
   * Messages below the session's current log level threshold are silently dropped.
   *
   * @param level the log level
   * @param message the log message
   */
  void log(LoggingLevel level, String message);

  /** Convenience method for {@link LoggingLevel#DEBUG}. */
  default void debug(String logger, Object data) {
    log(LoggingLevel.DEBUG, logger, data);
  }

  /** Convenience method for {@link LoggingLevel#INFO}. */
  default void info(String logger, Object data) {
    log(LoggingLevel.INFO, logger, data);
  }

  /** Convenience method for {@link LoggingLevel#NOTICE}. */
  default void notice(String logger, Object data) {
    log(LoggingLevel.NOTICE, logger, data);
  }

  /** Convenience method for {@link LoggingLevel#WARNING}. */
  default void warning(String logger, Object data) {
    log(LoggingLevel.WARNING, logger, data);
  }

  /** Convenience method for {@link LoggingLevel#ERROR}. */
  default void error(String logger, Object data) {
    log(LoggingLevel.ERROR, logger, data);
  }

  /** Convenience method for {@link LoggingLevel#CRITICAL}. */
  default void critical(String logger, Object data) {
    log(LoggingLevel.CRITICAL, logger, data);
  }

  /** Convenience method for {@link LoggingLevel#ALERT}. */
  default void alert(String logger, Object data) {
    log(LoggingLevel.ALERT, logger, data);
  }

  /** Convenience method for {@link LoggingLevel#EMERGENCY}. */
  default void emergency(String logger, Object data) {
    log(LoggingLevel.EMERGENCY, logger, data);
  }

  /**
   * Sends an elicitation request to the client using a builder-constructed schema, blocking until a
   * response is received. The caller configures the schema inline via the consumer.
   *
   * @param message the message to display to the user
   * @param schema a consumer that configures the {@link ElicitationSchemaBuilder}
   * @return the elicitation result containing the client's action and typed getters for content
   * @throws McpElicitationTimeoutException if the client does not respond within the timeout
   * @throws McpElicitationException if the response fails schema validation
   * @throws McpElicitationNotSupportedException if the client does not support elicitation
   */
  ElicitationResult elicit(String message, Consumer<ElicitationSchemaBuilder> schema);

  /**
   * Sends a {@code sampling/createMessage} request to the client, blocking until a response is
   * received. The client's LLM processes the prompt and returns the result.
   *
   * @param prompt the user prompt text
   * @param maxTokens the maximum number of tokens to generate
   * @return the sampling result containing the LLM response
   * @throws McpSamplingTimeoutException if the client does not respond within the timeout
   * @throws McpSamplingException if the response cannot be processed
   * @throws McpSamplingNotSupportedException if the client does not support sampling
   */
  SamplingResult sample(String prompt, int maxTokens);
}
