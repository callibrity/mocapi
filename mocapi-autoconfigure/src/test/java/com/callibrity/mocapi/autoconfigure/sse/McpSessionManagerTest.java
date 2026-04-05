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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class McpSessionManagerTest {

  private McpSessionManager manager;

  @AfterEach
  void tearDown() {
    if (manager != null) {
      manager.shutdown();
    }
  }

  @Test
  void createSessionShouldReturnUniqueSessions() {
    manager = new McpSessionManager();
    var session1 = manager.createSession();
    var session2 = manager.createSession();

    assertThat(session1.getSessionId()).isNotEqualTo(session2.getSessionId());
    assertThat(manager.getSessionCount()).isEqualTo(2);
  }

  @Test
  void getSessionShouldReturnSessionById() {
    manager = new McpSessionManager();
    var session = manager.createSession();

    var retrieved = manager.getSession(session.getSessionId());
    assertThat(retrieved).isPresent().hasValue(session);
  }

  @Test
  void getSessionShouldReturnEmptyForUnknownId() {
    manager = new McpSessionManager();
    assertThat(manager.getSession("nonexistent")).isEmpty();
  }

  @Test
  void getSessionShouldReturnEmptyForNullId() {
    manager = new McpSessionManager();
    assertThat(manager.getSession(null)).isEmpty();
  }

  @Test
  void getSessionShouldReturnEmptyForExpiredSession() throws Exception {
    manager = new McpSessionManager(0);
    var session = manager.createSession();

    Thread.sleep(50);
    assertThat(manager.getSession(session.getSessionId())).isEmpty();
  }

  @Test
  void terminateSessionShouldRemoveSession() {
    manager = new McpSessionManager();
    var session = manager.createSession();

    assertThat(manager.terminateSession(session.getSessionId())).isTrue();
    assertThat(manager.getSession(session.getSessionId())).isEmpty();
    assertThat(manager.getSessionCount()).isZero();
  }

  @Test
  void terminateSessionShouldReturnFalseForUnknownId() {
    manager = new McpSessionManager();
    assertThat(manager.terminateSession("nonexistent")).isFalse();
  }

  @Test
  void cleanupInactiveSessionsShouldRemoveExpiredSessions() throws Exception {
    manager = new McpSessionManager(0);
    manager.createSession();
    manager.createSession();

    Thread.sleep(50);
    manager.cleanupInactiveSessions();

    assertThat(manager.getSessionCount()).isZero();
  }

  @Test
  void cleanupInactiveSessionsShouldKeepActiveSessions() {
    manager = new McpSessionManager(3600);
    manager.createSession();

    manager.cleanupInactiveSessions();

    assertThat(manager.getSessionCount()).isEqualTo(1);
  }

  @Test
  void getSessionCountShouldReturnCorrectCount() {
    manager = new McpSessionManager();
    assertThat(manager.getSessionCount()).isZero();

    manager.createSession();
    assertThat(manager.getSessionCount()).isEqualTo(1);

    manager.createSession();
    assertThat(manager.getSessionCount()).isEqualTo(2);
  }
}
