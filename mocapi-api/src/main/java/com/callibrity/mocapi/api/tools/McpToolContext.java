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
   * Sends a DEBUG log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void debug(String logger, String message) {
    log(LoggingLevel.DEBUG, logger, message);
  }

  /**
   * Sends an INFO log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void info(String logger, String message) {
    log(LoggingLevel.INFO, logger, message);
  }

  /**
   * Sends a NOTICE log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void notice(String logger, String message) {
    log(LoggingLevel.NOTICE, logger, message);
  }

  /**
   * Sends a WARNING log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void warning(String logger, String message) {
    log(LoggingLevel.WARNING, logger, message);
  }

  /**
   * Sends an ERROR log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void error(String logger, String message) {
    log(LoggingLevel.ERROR, logger, message);
  }

  /**
   * Sends a CRITICAL log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void critical(String logger, String message) {
    log(LoggingLevel.CRITICAL, logger, message);
  }

  /**
   * Sends an ALERT log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void alert(String logger, String message) {
    log(LoggingLevel.ALERT, logger, message);
  }

  /**
   * Sends an EMERGENCY log notification to the client.
   *
   * @param logger the logger name
   * @param message the log message
   */
  default void emergency(String logger, String message) {
    log(LoggingLevel.EMERGENCY, logger, message);
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
   *     .string("name", "Your name").required()
   *     .string("email", "Email address").format("email").required()
   *     .integer("age", "Your age").min(0).max(150)
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
}
