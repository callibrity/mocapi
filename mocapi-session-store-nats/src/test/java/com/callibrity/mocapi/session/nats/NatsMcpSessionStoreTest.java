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
package com.callibrity.mocapi.session.nats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.session.McpSession;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.api.KeyValueStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class NatsMcpSessionStoreTest {

  private static final String BUCKET_NAME = "mocapi-sessions";
  private static final Duration SESSION_TIMEOUT = Duration.ofHours(1);

  @Container
  static GenericContainer<?> nats =
      new GenericContainer<>("nats:latest").withCommand("-js").withExposedPorts(4222);

  private Connection connection;
  private NatsMcpSessionStore store;

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeEach
  void setUp() throws Exception {
    connection = Nats.connect("nats://" + nats.getHost() + ":" + nats.getMappedPort(4222));
    try {
      connection.keyValueManagement().delete(BUCKET_NAME);
    } catch (Exception ignored) {
    }
    store =
        new NatsMcpSessionStore(
            connection, JsonMapper.builder().build(), BUCKET_NAME, SESSION_TIMEOUT);
  }

  @Test
  void saveThenFindReturnsSameSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent().hasValue(session);
  }

  @Test
  void saveAndUpdateThenFindReturnsUpdatedSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));

    McpSession updated = session.withLogLevel(LoggingLevel.DEBUG);
    store.update(session.sessionId(), updated);

    assertThat(store.find(session.sessionId())).isPresent().hasValue(updated);
  }

  @Test
  void touchOnExistingSessionKeepsItAccessible() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    store.touch(session.sessionId(), Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent().hasValue(session);
  }

  @Test
  void deleteRemovesSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    store.delete(session.sessionId());
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void findOnMissingKeyReturnsEmpty() {
    assertThat(store.find("nonexistent")).isEmpty();
  }

  @Test
  void deleteOnMissingKeyDoesNotThrow() {
    assertThatCode(() -> store.delete("nonexistent")).doesNotThrowAnyException();
  }

  @Test
  void touchOnMissingKeyDoesNotThrowOrCreateEntry() {
    assertThatCode(() -> store.touch("nonexistent", Duration.ofHours(1)))
        .doesNotThrowAnyException();
    assertThat(store.find("nonexistent")).isEmpty();
  }

  @Test
  void bucketIsCreatedAtStartupWithExpectedConfig() throws Exception {
    KeyValueStatus status = connection.keyValueManagement().getBucketInfo(BUCKET_NAME);
    assertThat(status.getMaxHistoryPerKey()).isEqualTo(1);
    assertThat(status.getTtl()).isEqualTo(SESSION_TIMEOUT);
  }

  @Nested
  @Testcontainers
  class TtlFilteringTest {

    @Container
    static GenericContainer<?> shortTtlNats =
        new GenericContainer<>("nats:latest").withCommand("-js").withExposedPorts(4222);

    @Test
    void sessionExpiresAfterBucketTtl() throws Exception {
      String shortBucket = "mocapi-sessions-short";
      Duration shortTtl = Duration.ofSeconds(1);
      Connection shortConn =
          Nats.connect("nats://" + shortTtlNats.getHost() + ":" + shortTtlNats.getMappedPort(4222));
      NatsMcpSessionStore shortStore =
          new NatsMcpSessionStore(shortConn, JsonMapper.builder().build(), shortBucket, shortTtl);

      McpSession session = sessionWithId();
      shortStore.save(session, shortTtl);

      await()
          .atMost(Duration.ofSeconds(5))
          .until(() -> shortStore.find(session.sessionId()).isEmpty());
    }
  }
}
