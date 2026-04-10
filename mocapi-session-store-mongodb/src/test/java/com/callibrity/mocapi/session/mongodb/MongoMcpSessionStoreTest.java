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
package com.callibrity.mocapi.session.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.RootsCapability;
import com.callibrity.mocapi.model.SamplingCapability;
import com.callibrity.mocapi.session.McpSession;
import com.mongodb.client.MongoClients;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class MongoMcpSessionStoreTest {

  private static final String COLLECTION = "mocapi_sessions";

  @Container static MongoDBContainer mongodb = new MongoDBContainer("mongo:7");

  private static MongoTemplate mongoTemplate;
  private MongoMcpSessionStore store;

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeAll
  static void initMongo() {
    var client = MongoClients.create(mongodb.getConnectionString());
    mongoTemplate = new MongoTemplate(client, "mocapi_test");
  }

  @BeforeEach
  void setUp() {
    mongoTemplate.dropCollection(COLLECTION);
    store = new MongoMcpSessionStore(mongoTemplate, new JsonMapper(), COLLECTION);
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
  void findFiltersExpiredDocuments() {
    McpSession session = sessionWithId();
    var doc =
        new Document("_id", session.sessionId())
            .append("payload", new JsonMapper().writeValueAsString(session))
            .append("expiresAt", Date.from(Instant.now().minusSeconds(60)));
    mongoTemplate.getCollection(COLLECTION).insertOne(doc);

    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void updatePreservesExpiresAt() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));

    Document before =
        mongoTemplate
            .getCollection(COLLECTION)
            .find(new Document("_id", session.sessionId()))
            .first();
    Date originalExpiresAt = before.getDate("expiresAt");

    McpSession updated = session.withLogLevel(LoggingLevel.DEBUG);
    store.update(session.sessionId(), updated);

    assertThat(store.find(session.sessionId())).isPresent().hasValue(updated);

    Document after =
        mongoTemplate
            .getCollection(COLLECTION)
            .find(new Document("_id", session.sessionId()))
            .first();
    assertThat(after.getDate("expiresAt")).isEqualTo(originalExpiresAt);
  }

  @Test
  void touchUpdatesOnlyExpiresAt() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofMinutes(5));

    Document before =
        mongoTemplate
            .getCollection(COLLECTION)
            .find(new Document("_id", session.sessionId()))
            .first();
    String originalPayload = before.getString("payload");

    store.touch(session.sessionId(), Duration.ofHours(2));

    Document after =
        mongoTemplate
            .getCollection(COLLECTION)
            .find(new Document("_id", session.sessionId()))
            .first();
    assertThat(after.getString("payload")).isEqualTo(originalPayload);
    assertThat(after.getDate("expiresAt")).isAfter(before.getDate("expiresAt"));
  }

  @Test
  void ttlIndexExists() {
    var indexes = mongoTemplate.getCollection(COLLECTION).listIndexes();
    boolean found = false;
    for (Document idx : indexes) {
      Document key = idx.get("key", Document.class);
      if (key != null && key.containsKey("expiresAt")) {
        assertThat(((Number) idx.get("expireAfterSeconds")).longValue()).isEqualTo(0L);
        found = true;
      }
    }
    assertThat(found).isTrue();
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
