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
package com.callibrity.mocapi.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Default in-memory implementation of {@link McpSessionStore}. */
@Slf4j
public class InMemoryMcpSessionStore implements McpSessionStore {

  private static final long CLEANUP_INTERVAL_SECONDS = 300L;

  private record Entry(McpSession session, Instant expiresAt) {}

  private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
  private final ScheduledExecutorService cleanupExecutor;

  public InMemoryMcpSessionStore() {
    this.cleanupExecutor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "mcp-session-cleanup");
              t.setDaemon(true);
              return t;
            });
    this.cleanupExecutor.scheduleAtFixedRate(
        this::cleanupExpired, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    sessions.put(session.sessionId(), new Entry(session, Instant.now().plus(ttl)));
    log.debug("Saved MCP session: {}", session.sessionId());
  }

  @Override
  public void update(String sessionId, McpSession session) {
    sessions.computeIfPresent(sessionId, (_, entry) -> new Entry(session, entry.expiresAt()));
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }
    Entry entry = sessions.get(sessionId);
    if (entry == null) {
      return Optional.empty();
    }
    if (Instant.now().isAfter(entry.expiresAt())) {
      sessions.remove(sessionId);
      log.debug("Session expired: {}", sessionId);
      return Optional.empty();
    }
    return Optional.of(entry.session());
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    sessions.computeIfPresent(
        sessionId, (_, entry) -> new Entry(entry.session(), Instant.now().plus(ttl)));
  }

  @Override
  public void delete(String sessionId) {
    sessions.remove(sessionId);
    log.debug("Deleted MCP session: {}", sessionId);
  }

  /** Shuts down the internal cleanup executor. */
  public void shutdown() {
    cleanupExecutor.shutdownNow();
  }

  void cleanupExpired() {
    int sizeBefore = sessions.size();
    Instant now = Instant.now();
    sessions.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    int removed = sizeBefore - sessions.size();
    if (removed > 0) {
      log.info("Cleaned up {} expired MCP sessions", removed);
    }
  }

  int getSessionCount() {
    return sessions.size();
  }
}
