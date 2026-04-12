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
package com.callibrity.mocapi.protocol.tools;

import com.callibrity.mocapi.model.LoggingLevel;

/**
 * Context available to interactive tool methods. Provides methods for sending progress
 * notifications, log messages, and the final result during tool execution.
 *
 * @param <R> the result type of the tool
 */
public interface McpToolContext<R> {

  ScopedValue<McpToolContext<?>> CURRENT = ScopedValue.newInstance();

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
   * Sets the final result of the tool invocation. This is a terminal operation — calling it twice
   * throws {@link IllegalStateException}.
   *
   * @param result the tool result
   */
  void sendResult(R result);

  /** Placeholder — throws {@link UnsupportedOperationException}. */
  default Object elicit(String message) {
    throw new UnsupportedOperationException(
        "Elicitation is not yet supported in the protocol layer");
  }

  /** Placeholder — throws {@link UnsupportedOperationException}. */
  default Object sample(String prompt, int maxTokens) {
    throw new UnsupportedOperationException("Sampling is not yet supported in the protocol layer");
  }
}
