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
package com.callibrity.mocapi.session.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.RootsCapability;
import com.callibrity.mocapi.model.SamplingCapability;
import com.callibrity.mocapi.session.McpSession;
import com.datastax.oss.driver.api.core.CqlSession;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class CassandraMcpSessionStoreTest {

  private static final String KEYSPACE = "mocapi_test";
  private static final String TABLE = "mocapi_sessions";

  @Container static CassandraContainer<?> cassandra = new CassandraContainer<>("cassandra:5");

  private static CqlSession cqlSession;
  private CassandraMcpSessionStore store;

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeAll
  static void initSchema() {
    cqlSession =
        CqlSession.builder()
            .addContactPoint(cassandra.getContactPoint())
            .withLocalDatacenter(cassandra.getLocalDatacenter())
            .build();
    cqlSession.execute(
        "CREATE KEYSPACE IF NOT EXISTS "
            + KEYSPACE
            + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
    cqlSession.execute(
        "CREATE TABLE IF NOT EXISTS "
            + KEYSPACE
            + "."
            + TABLE
            + " (session_id text PRIMARY KEY, payload text)");
  }

  @BeforeEach
  void setUp() {
    cqlSession.execute("TRUNCATE " + KEYSPACE + "." + TABLE);
    store =
        new CassandraMcpSessionStore(
            cqlSession,
            new JsonMapper(),
            KEYSPACE,
            TABLE,
            com.datastax.oss.driver.api.core.DefaultConsistencyLevel.LOCAL_ONE,
            com.datastax.oss.driver.api.core.DefaultConsistencyLevel.LOCAL_ONE);
  }

  @Test
  void saveThenFindReturnsSameSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent().hasValue(session);
  }

  @Test
  void roundTripsAllFields() {
    McpSession session =
        new McpSession(
                "2025-11-25",
                new ClientCapabilities(
                    new RootsCapability(true),
                    new SamplingCapability(),
                    new ElicitationCapability()),
                new Implementation("test-client", "Test Client", "2.0"),
                LoggingLevel.DEBUG)
            .withSessionId(UUID.randomUUID().toString());

    store.save(session, Duration.ofHours(1));

    McpSession found = store.find(session.sessionId()).orElseThrow();
    assertThat(found.protocolVersion()).isEqualTo("2025-11-25");
    assertThat(found.capabilities().roots().listChanged()).isTrue();
    assertThat(found.capabilities().sampling()).isNotNull();
    assertThat(found.capabilities().elicitation()).isNotNull();
    assertThat(found.clientInfo().name()).isEqualTo("test-client");
    assertThat(found.clientInfo().title()).isEqualTo("Test Client");
    assertThat(found.clientInfo().version()).isEqualTo("2.0");
    assertThat(found.logLevel()).isEqualTo(LoggingLevel.DEBUG);
    assertThat(found.sessionId()).isEqualTo(session.sessionId());
  }

  @Test
  void saveWithShortTtlExpiresSession() throws InterruptedException {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(1));
    Thread.sleep(2_000);
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void updatePreservesTtl() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(10));

    McpSession updated = session.withLogLevel(LoggingLevel.DEBUG);
    store.update(session.sessionId(), updated);

    assertThat(store.find(session.sessionId())).isPresent().hasValue(updated);

    var ttlRow =
        cqlSession
            .execute(
                "SELECT TTL(payload) FROM " + KEYSPACE + "." + TABLE + " WHERE session_id = ?",
                session.sessionId())
            .one();
    assertThat(ttlRow).isNotNull();
    int remainingTtl = ttlRow.getInt(0);
    assertThat(remainingTtl).isGreaterThan(5).isLessThanOrEqualTo(10);
  }

  @Test
  void touchExtendsTtl() throws InterruptedException {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(1));
    store.touch(session.sessionId(), Duration.ofSeconds(10));
    Thread.sleep(2_000);
    assertThat(store.find(session.sessionId())).isPresent();
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
}
