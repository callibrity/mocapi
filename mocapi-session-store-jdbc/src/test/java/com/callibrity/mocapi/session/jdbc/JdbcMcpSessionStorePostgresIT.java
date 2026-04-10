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
package com.callibrity.mocapi.session.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.session.McpSession;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class JdbcMcpSessionStorePostgresIT {

  private static final String TABLE_NAME = "mocapi_sessions";

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private JdbcTemplate jdbcTemplate;
  private JdbcMcpSessionStore store;

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeEach
  void setUp() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    new ResourceDatabasePopulator(
            new ClassPathResource("schema/mocapi-session-store-postgresql.sql"))
        .execute(dataSource);
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.update("DELETE FROM " + TABLE_NAME);
    store =
        new JdbcMcpSessionStore(
            JdbcClient.create(dataSource),
            new JsonMapper(),
            JdbcDialect.POSTGRESQL,
            TABLE_NAME,
            false,
            Duration.ZERO);
  }

  @Test
  void saveThenFindReturnsSameSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent().hasValue(session);
  }

  @Test
  void saveWithShortTtlExpiresSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(1));
    await().atMost(Duration.ofSeconds(3)).until(() -> store.find(session.sessionId()).isEmpty());
  }

  @Test
  void touchExtendsTtl() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(1));
    store.touch(session.sessionId(), Duration.ofSeconds(10));
    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(store.find(session.sessionId())).isPresent());
  }

  @Test
  void updatePreservesTtl() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(10));

    Timestamp originalExpiry =
        jdbcTemplate.queryForObject(
            "SELECT expires_at FROM " + TABLE_NAME + " WHERE session_id = ?",
            Timestamp.class,
            session.sessionId());

    McpSession updated = session.withLogLevel(LoggingLevel.DEBUG);
    store.update(session.sessionId(), updated);

    assertThat(store.find(session.sessionId())).isPresent().hasValue(updated);

    Timestamp afterUpdate =
        jdbcTemplate.queryForObject(
            "SELECT expires_at FROM " + TABLE_NAME + " WHERE session_id = ?",
            Timestamp.class,
            session.sessionId());
    assertThat(afterUpdate).isEqualTo(originalExpiry);
  }

  @Test
  void deleteRemovesEntry() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    store.delete(session.sessionId());
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void findOnNonExistentKeyReturnsEmpty() {
    assertThat(store.find("nonexistent")).isEmpty();
  }

  @Test
  void deleteOnNonExistentKeyDoesNotFail() {
    store.delete("nonexistent");
  }

  @Test
  void cleanupDeletesExpiredRows() {
    String sessionId = UUID.randomUUID().toString();
    String json = new JsonMapper().writeValueAsString(sessionWithId().withSessionId(sessionId));
    jdbcTemplate.update(
        "INSERT INTO " + TABLE_NAME + " (session_id, payload, expires_at) VALUES (?, ?, ?)",
        sessionId,
        json,
        Timestamp.from(Instant.now().minusSeconds(60)));

    store.cleanupExpired();

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE session_id = ?",
            Integer.class,
            sessionId);
    assertThat(count).isZero();
  }

  @Test
  void concurrentUpsertBothSucceed() throws InterruptedException {
    String sessionId = UUID.randomUUID().toString();
    McpSession session1 =
        new McpSession(
                "2025-11-25",
                new ClientCapabilities(null, null, null),
                new Implementation("client-1", null, "1.0"))
            .withSessionId(sessionId);
    McpSession session2 =
        new McpSession(
                "2025-11-25",
                new ClientCapabilities(null, null, null),
                new Implementation("client-2", null, "2.0"))
            .withSessionId(sessionId);

    var start = new java.util.concurrent.CountDownLatch(1);
    var error1 = new java.util.concurrent.atomic.AtomicReference<Exception>();
    var error2 = new java.util.concurrent.atomic.AtomicReference<Exception>();

    Thread t1 =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    start.await();
                    store.save(session1, Duration.ofHours(1));
                  } catch (Exception e) {
                    error1.set(e);
                  }
                });
    Thread t2 =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    start.await();
                    store.save(session2, Duration.ofHours(1));
                  } catch (Exception e) {
                    error2.set(e);
                  }
                });

    start.countDown();
    t1.join(5_000);
    t2.join(5_000);

    assertThat(error1.get()).isNull();
    assertThat(error2.get()).isNull();
    assertThat(store.find(sessionId)).isPresent();
  }
}
