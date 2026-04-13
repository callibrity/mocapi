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

import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;

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
   * Sends an elicitation request to the client and blocks until the client responds.
   *
   * @param params the elicitation request parameters
   * @return the client's elicitation result
   */
  ElicitResult elicit(ElicitRequestFormParams params);

  /**
   * Sends a sampling (createMessage) request to the client and blocks until the client responds.
   *
   * @param params the create-message request parameters
   * @return the client's sampling result
   */
  CreateMessageResult sample(CreateMessageRequestParams params);
}
