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
package com.callibrity.mocapi.server;

import tools.jackson.core.type.TypeReference;

/**
 * Handle that MCP handler methods can declare as a parameter to opt into SSE streaming. Provides
 * methods for sending intermediate messages (progress notifications, arbitrary notifications) to
 * the client during a long-running operation, and for eliciting structured input from the client.
 */
public interface McpStreamContext {

  /**
   * Sends a progress notification to the client.
   *
   * @param progress the current progress value
   * @param total the total expected value
   */
  void sendProgress(long progress, long total);

  /**
   * Sends an arbitrary notification to the client.
   *
   * @param method the notification method name
   * @param params the notification parameters (may be null)
   */
  void sendNotification(String method, Object params);

  /**
   * Sends a log notification ({@code notifications/message}) to the client.
   *
   * @param level the log level (e.g. "debug", "info", "warning", "error")
   * @param logger the logger name (typically the tool name)
   * @param data the log data (message string or structured object)
   */
  void log(String level, String logger, Object data);

  /**
   * Sends a log notification ({@code notifications/message}) to the client using a default logger.
   *
   * @param level the log level (e.g. "debug", "info", "warning", "error")
   * @param message the log message
   */
  void log(String level, String message);

  /**
   * Sends an elicitation request to the client, blocking until a response is received. The client
   * is asked to fill out a form whose schema is generated from the given type.
   *
   * @param message the message to display to the user
   * @param type the class to generate the form schema from
   * @param <T> the expected response type
   * @return the elicitation result containing the client's action and typed content
   * @throws McpElicitationTimeoutException if the client does not respond within the timeout
   * @throws McpElicitationException if the response fails schema validation
   * @throws McpElicitationNotSupportedException if the client does not support elicitation
   */
  <T> ElicitationResult<T> elicitForm(String message, Class<T> type);

  /**
   * Sends an elicitation request to the client, blocking until a response is received. The client
   * is asked to fill out a form whose schema is generated from the given type reference.
   *
   * @param message the message to display to the user
   * @param type the type reference to generate the form schema from
   * @param <T> the expected response type
   * @return the elicitation result containing the client's action and typed content
   * @throws McpElicitationTimeoutException if the client does not respond within the timeout
   * @throws McpElicitationException if the response fails schema validation
   * @throws McpElicitationNotSupportedException if the client does not support elicitation
   */
  <T> ElicitationResult<T> elicitForm(String message, TypeReference<T> type);
}
