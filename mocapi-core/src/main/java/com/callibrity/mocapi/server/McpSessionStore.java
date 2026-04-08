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

import java.time.Duration;
import java.util.Optional;

/** SPI for persisting MCP session data (client capabilities, client info, protocol version). */
public interface McpSessionStore {

  /**
   * Saves a session and returns its generated ID.
   *
   * @param session the session data to persist
   * @param ttl how long the session should live before expiring
   * @return the generated session ID
   */
  String save(McpSession session, Duration ttl);

  /**
   * Finds a session by ID.
   *
   * @param sessionId the session ID
   * @return the session, or empty if not found or expired
   */
  Optional<McpSession> find(String sessionId);

  /**
   * Refreshes the TTL of a session.
   *
   * @param sessionId the session ID
   * @param ttl the new TTL
   */
  void touch(String sessionId, Duration ttl);

  /**
   * Deletes a session.
   *
   * @param sessionId the session ID
   */
  void delete(String sessionId);
}
