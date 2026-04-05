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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages MCP sessions for SSE streaming support.
 *
 * <p>Provides session lifecycle management including:
 *
 * <ul>
 *   <li>Session creation
 *   <li>Session retrieval and validation
 *   <li>Automatic cleanup of inactive sessions
 * </ul>
 *
 * <p>Thread-safe for concurrent access.
 */
@Slf4j
public class McpSessionManager {

  // ------------------------------ FIELDS ------------------------------

  /** Default session timeout in seconds (1 hour). */
  private static final long DEFAULT_SESSION_TIMEOUT_SECONDS = 3600L;

  private static final long CLEANUP_INTERVAL_SECONDS = 300L;

  private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();
  private final long sessionTimeoutSeconds;
  private final ScheduledExecutorService cleanupExecutor;

  // --------------------------- CONSTRUCTORS ---------------------------

  /** Creates a session manager with the default timeout. */
  public McpSessionManager() {
    this(DEFAULT_SESSION_TIMEOUT_SECONDS);
  }

  /**
   * Creates a session manager with a custom timeout.
   *
   * @param sessionTimeoutSeconds the session timeout in seconds
   */
  public McpSessionManager(long sessionTimeoutSeconds) {
    this.sessionTimeoutSeconds = sessionTimeoutSeconds;
    this.cleanupExecutor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "mcp-session-cleanup");
              t.setDaemon(true);
              return t;
            });
    this.cleanupExecutor.scheduleAtFixedRate(
        this::cleanupInactiveSessions,
        CLEANUP_INTERVAL_SECONDS,
        CLEANUP_INTERVAL_SECONDS,
        TimeUnit.SECONDS);
  }

  // -------------------------- OTHER METHODS --------------------------

  /**
   * Creates a new MCP session.
   *
   * @return the newly created session
   */
  public McpSession createSession() {
    McpSession session = new McpSession();
    sessions.put(session.getSessionId(), session);
    log.debug("Created new MCP session: {}", session.getSessionId());
    return session;
  }

  /**
   * Retrieves a session by ID.
   *
   * @param sessionId the session ID
   * @return the session, or empty if not found or expired
   */
  public Optional<McpSession> getSession(String sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }

    McpSession session = sessions.get(sessionId);
    if (session == null) {
      log.debug("Session not found: {}", sessionId);
      return Optional.empty();
    }

    if (session.isInactive(sessionTimeoutSeconds)) {
      log.debug("Session expired: {}", sessionId);
      sessions.remove(sessionId);
      return Optional.empty();
    }

    return Optional.of(session);
  }

  /**
   * Terminates a session, removing it from the manager.
   *
   * @param sessionId the session ID to terminate
   * @return true if the session was found and removed, false otherwise
   */
  public boolean terminateSession(String sessionId) {
    McpSession removed = sessions.remove(sessionId);
    if (removed != null) {
      log.debug("Terminated MCP session: {}", sessionId);
      return true;
    }
    return false;
  }

  /** Cleans up inactive sessions. Runs periodically via the internal executor. */
  void cleanupInactiveSessions() {
    int sizeBefore = sessions.size();
    sessions.entrySet().removeIf(e -> e.getValue().isInactive(sessionTimeoutSeconds));
    int removed = sizeBefore - sessions.size();
    if (removed > 0) {
      log.info("Cleaned up {} inactive MCP sessions", removed);
    }
  }

  /** Shuts down the internal cleanup executor. */
  public void shutdown() {
    cleanupExecutor.shutdownNow();
  }

  /**
   * Returns the number of active sessions.
   *
   * @return the session count
   */
  public int getSessionCount() {
    return sessions.size();
  }
}
