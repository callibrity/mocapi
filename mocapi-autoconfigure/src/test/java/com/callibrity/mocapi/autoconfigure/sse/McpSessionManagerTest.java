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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;

class McpSessionManagerTest {

  private McpSessionManager manager;
  private OdysseyStreamRegistry registry;

  @BeforeEach
  void setUp() {
    registry = mock(OdysseyStreamRegistry.class);
    when(registry.channel(anyString())).thenReturn(mock(OdysseyStream.class));
  }

  @AfterEach
  void tearDown() {
    if (manager != null) {
      manager.shutdown();
    }
  }

  @Test
  void createSessionShouldReturnUniqueSessions() {
    manager = new McpSessionManager(registry);
    var session1 = manager.createSession();
    var session2 = manager.createSession();

    assertThat(session1.getSessionId()).isNotEqualTo(session2.getSessionId());
    assertThat(manager.getSessionCount()).isEqualTo(2);
  }

  @Test
  void getSessionShouldReturnSessionById() {
    manager = new McpSessionManager(registry);
    var session = manager.createSession();

    var retrieved = manager.getSession(session.getSessionId());
    assertThat(retrieved).isPresent().hasValue(session);
  }

  @Test
  void getSessionShouldReturnEmptyForUnknownId() {
    manager = new McpSessionManager(registry);
    assertThat(manager.getSession("nonexistent")).isEmpty();
  }

  @Test
  void getSessionShouldReturnEmptyForNullId() {
    manager = new McpSessionManager(registry);
    assertThat(manager.getSession(null)).isEmpty();
  }

  @Test
  void getSessionShouldReturnEmptyForExpiredSession() {
    manager = new McpSessionManager(registry, 0);
    var session = manager.createSession();

    assertThat(manager.getSession(session.getSessionId())).isEmpty();
  }

  @Test
  void terminateSessionShouldRemoveSession() {
    manager = new McpSessionManager(registry);
    var session = manager.createSession();

    assertThat(manager.terminateSession(session.getSessionId())).isTrue();
    assertThat(manager.getSession(session.getSessionId())).isEmpty();
    assertThat(manager.getSessionCount()).isZero();
  }

  @Test
  void terminateSessionShouldDeleteNotificationStream() {
    OdysseyStream stream = mock(OdysseyStream.class);
    when(registry.channel(anyString())).thenReturn(stream);
    manager = new McpSessionManager(registry);
    var session = manager.createSession();

    manager.terminateSession(session.getSessionId());

    verify(stream).delete();
  }

  @Test
  void terminateSessionShouldReturnFalseForUnknownId() {
    manager = new McpSessionManager(registry);
    assertThat(manager.terminateSession("nonexistent")).isFalse();
  }

  @Test
  void cleanupInactiveSessionsShouldRemoveExpiredSessions() {
    manager = new McpSessionManager(registry, 0);
    manager.createSession();
    manager.createSession();

    manager.cleanupInactiveSessions();

    assertThat(manager.getSessionCount()).isZero();
  }

  @Test
  void cleanupInactiveSessionsShouldKeepActiveSessions() {
    manager = new McpSessionManager(registry, 3600);
    manager.createSession();

    manager.cleanupInactiveSessions();

    assertThat(manager.getSessionCount()).isEqualTo(1);
  }

  @Test
  void getSessionCountShouldReturnCorrectCount() {
    manager = new McpSessionManager(registry);
    assertThat(manager.getSessionCount()).isZero();

    manager.createSession();
    assertThat(manager.getSessionCount()).isEqualTo(1);

    manager.createSession();
    assertThat(manager.getSessionCount()).isEqualTo(2);
  }
}
