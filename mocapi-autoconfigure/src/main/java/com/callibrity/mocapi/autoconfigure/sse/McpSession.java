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
package com.callibrity.mocapi.autoconfigure.sse;

import java.time.Instant;
import lombok.Getter;
import org.jwcarman.odyssey.core.OdysseyStream;

/**
 * Represents an MCP session with identity and lifecycle metadata.
 *
 * <p>Each session has a unique ID and tracks:
 *
 * <ul>
 *   <li>Session creation time
 *   <li>Last activity timestamp
 *   <li>An {@link OdysseyStream} for server-initiated notifications
 * </ul>
 *
 * <p>Thread-safe for concurrent access from multiple request handlers.
 *
 * @see McpSessionManager
 */
@Getter
public class McpSession {

  // ------------------------------ FIELDS ------------------------------

  private final String sessionId;
  private final Instant createdAt;
  private final OdysseyStream notificationStream;
  private volatile Instant lastActivity;

  // --------------------------- CONSTRUCTORS ---------------------------

  /**
   * Creates a new MCP session with a cryptographically secure UUID.
   *
   * @param sessionId the unique session identifier
   * @param notificationStream the Odyssey stream for server-initiated notifications
   */
  public McpSession(String sessionId, OdysseyStream notificationStream) {
    this.sessionId = sessionId;
    this.createdAt = Instant.now();
    this.lastActivity = Instant.now();
    this.notificationStream = notificationStream;
  }

  // -------------------------- OTHER METHODS --------------------------

  /** Updates the last activity timestamp for session timeout tracking. */
  public void updateActivity() {
    this.lastActivity = Instant.now();
  }

  /**
   * Checks if the session has been inactive for longer than the specified duration.
   *
   * @param inactiveSeconds the number of seconds of inactivity to check
   * @return true if the session has been inactive for longer than the specified duration
   */
  public boolean isInactive(long inactiveSeconds) {
    return Instant.now().isAfter(lastActivity.plusSeconds(inactiveSeconds));
  }
}
