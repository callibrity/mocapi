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

import com.callibrity.mocapi.client.ClientCapabilities;
import com.callibrity.mocapi.client.ClientInfo;
import com.callibrity.mocapi.server.McpSession;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryMcpSessionStoreTest {

  private InMemoryMcpSessionStore store;

  private static final McpSession SESSION =
      new McpSession(
          "2025-11-25",
          new ClientCapabilities(null, null, null, null, null),
          new ClientInfo("test-client", null, "1.0", null, null, null));

  @BeforeEach
  void setUp() {
    store = new InMemoryMcpSessionStore();
  }

  @AfterEach
  void tearDown() {
    store.shutdown();
  }

  @Test
  void saveShouldReturnUniqueIds() {
    String id1 = store.save(SESSION, Duration.ofHours(1));
    String id2 = store.save(SESSION, Duration.ofHours(1));
    assertThat(id1).isNotEqualTo(id2);
  }

  @Test
  void findShouldReturnSavedSession() {
    String id = store.save(SESSION, Duration.ofHours(1));
    var found = store.find(id);
    assertThat(found).isPresent().hasValue(SESSION);
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
    String id = store.save(SESSION, Duration.ZERO);
    assertThat(store.find(id)).isEmpty();
  }

  @Test
  void touchShouldRefreshTtl() {
    String id = store.save(SESSION, Duration.ofMillis(1));
    store.touch(id, Duration.ofHours(1));
    assertThat(store.find(id)).isPresent();
  }

  @Test
  void deleteShouldRemoveSession() {
    String id = store.save(SESSION, Duration.ofHours(1));
    store.delete(id);
    assertThat(store.find(id)).isEmpty();
  }

  @Test
  void cleanupExpiredShouldRemoveExpiredSessions() {
    store.save(SESSION, Duration.ZERO);
    store.save(SESSION, Duration.ZERO);
    store.cleanupExpired();
    assertThat(store.getSessionCount()).isZero();
  }

  @Test
  void cleanupExpiredShouldKeepActiveSessions() {
    store.save(SESSION, Duration.ofHours(1));
    store.cleanupExpired();
    assertThat(store.getSessionCount()).isEqualTo(1);
  }

  @Test
  void getSessionCountShouldReturnCorrectCount() {
    assertThat(store.getSessionCount()).isZero();
    store.save(SESSION, Duration.ofHours(1));
    assertThat(store.getSessionCount()).isEqualTo(1);
    store.save(SESSION, Duration.ofHours(1));
    assertThat(store.getSessionCount()).isEqualTo(2);
  }
}
