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

/**
 * Handle that MCP handler methods can declare as a parameter to opt into SSE streaming. Provides
 * methods for sending intermediate messages (progress notifications, arbitrary notifications) to
 * the client during a long-running operation.
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
}
