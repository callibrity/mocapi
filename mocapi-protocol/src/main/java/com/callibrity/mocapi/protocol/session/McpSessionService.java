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
package com.callibrity.mocapi.protocol.session;

import com.callibrity.mocapi.model.LoggingLevel;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Orchestrates session lifecycle: create, find, delete, and log-level updates. */
public class McpSessionService {

  private final McpSessionStore store;
  private final Duration ttl;

  public McpSessionService(McpSessionStore store, Duration ttl) {
    this.store = store;
    this.ttl = ttl;
  }

  /** Generates a session ID, saves the session to the store, and returns the ID. */
  public String create(McpSession session) {
    String sessionId = UUID.randomUUID().toString();
    store.save(session.withSessionId(sessionId), ttl);
    return sessionId;
  }

  /** Looks up a session by ID, extending TTL on hit. */
  public Optional<McpSession> find(String sessionId) {
    Optional<McpSession> result = store.find(sessionId);
    result.ifPresent(_ -> store.touch(sessionId, ttl));
    return result;
  }

  /** Removes a session from the store. */
  public void delete(String sessionId) {
    store.delete(sessionId);
  }

  /** Updates the log level for the given session. */
  public void setLogLevel(String sessionId, LoggingLevel level) {
    store
        .find(sessionId)
        .ifPresent(session -> store.update(sessionId, session.withLogLevel(level)));
  }
}
