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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryMcpSessionStoreTest {

  private InMemoryMcpSessionStore store;

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new com.callibrity.mocapi.model.ClientCapabilities(null, null, null),
            new com.callibrity.mocapi.model.Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeEach
  void setUp() {
    store = new InMemoryMcpSessionStore();
  }

  @AfterEach
  void tearDown() {
    store.shutdown();
  }

  @Test
  void saveShouldStoreSessionById() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent();
  }

  @Test
  void findShouldReturnSavedSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    var found = store.find(session.sessionId());
    assertThat(found).isPresent().hasValue(session);
  }

  @Test
  void findShouldReturnEmptyForUnknownId() {
    assertThat(store.find("nonexistent")).isEmpty();
  }

  @Test
  void findShouldReturnEmptyForNullId() {
    assertThat(store.find(null)).isEmpty();
  }

  @Test
  void findShouldReturnEmptyForExpiredSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ZERO);
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void touchShouldRefreshTtl() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofMillis(1));
    store.touch(session.sessionId(), Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent();
  }

  @Test
  void deleteShouldRemoveSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    store.delete(session.sessionId());
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void cleanupExpiredShouldRemoveExpiredSessions() {
    store.save(sessionWithId(), Duration.ZERO);
    store.save(sessionWithId(), Duration.ZERO);
    store.cleanupExpired();
    assertThat(store.getSessionCount()).isZero();
  }

  @Test
  void cleanupExpiredShouldKeepActiveSessions() {
    store.save(sessionWithId(), Duration.ofHours(1));
    store.cleanupExpired();
    assertThat(store.getSessionCount()).isEqualTo(1);
  }

  @Test
  void updateShouldReplaceExistingSession() {
    McpSession original = sessionWithId();
    store.save(original, Duration.ofHours(1));
    McpSession updated = original.withLogLevel(com.callibrity.mocapi.model.LoggingLevel.DEBUG);
    store.update(original.sessionId(), updated);
    var found = store.find(original.sessionId());
    assertThat(found).isPresent().hasValue(updated);
  }

  @Test
  void updateShouldDoNothingForUnknownSession() {
    McpSession session = sessionWithId();
    store.update(session.sessionId(), session);
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void getSessionCountShouldReturnCorrectCount() {
    assertThat(store.getSessionCount()).isZero();
    store.save(sessionWithId(), Duration.ofHours(1));
    assertThat(store.getSessionCount()).isEqualTo(1);
    store.save(sessionWithId(), Duration.ofHours(1));
    assertThat(store.getSessionCount()).isEqualTo(2);
  }
}
