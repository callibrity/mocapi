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
package com.callibrity.mocapi.session.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.RootsCapability;
import com.callibrity.mocapi.model.SamplingCapability;
import com.callibrity.mocapi.session.McpSession;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class DynamoDbMcpSessionStoreTest {

  private static final String TABLE_NAME = "mocapi_sessions";

  @Container
  static LocalStackContainer localstack =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
          .withServices(LocalStackContainer.Service.DYNAMODB);

  private static DynamoDbClient dynamoDbClient;
  private DynamoDbMcpSessionStore store;

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeAll
  static void initDynamoDb() {
    dynamoDbClient =
        DynamoDbClient.builder()
            .endpointOverride(
                URI.create(
                    localstack
                        .getEndpointOverride(LocalStackContainer.Service.DYNAMODB)
                        .toString()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        localstack.getAccessKey(), localstack.getSecretKey())))
            .region(Region.of(localstack.getRegion()))
            .build();
  }

  private static void createTable() {
    dynamoDbClient.createTable(
        CreateTableRequest.builder()
            .tableName(TABLE_NAME)
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName("session_id")
                    .keyType(KeyType.HASH)
                    .build())
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("session_id")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .billingMode("PAY_PER_REQUEST")
            .build());
  }

  private static void deleteTable() {
    try {
      dynamoDbClient.deleteTable(DeleteTableRequest.builder().tableName(TABLE_NAME).build());
    } catch (Exception ignored) {
    }
  }

  @BeforeEach
  void setUp() {
    deleteTable();
    createTable();
    var properties = new DynamoDbSessionStoreProperties();
    properties.setTableName(TABLE_NAME);
    store = new DynamoDbMcpSessionStore(dynamoDbClient, new JsonMapper(), properties);
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
  void findFiltersExpiredItems() {
    McpSession session = sessionWithId();
    long pastEpoch = Instant.now().minusSeconds(60).getEpochSecond();
    dynamoDbClient.putItem(
        PutItemRequest.builder()
            .tableName(TABLE_NAME)
            .item(
                Map.of(
                    "session_id", AttributeValue.fromS(session.sessionId()),
                    "payload", AttributeValue.fromS(new JsonMapper().writeValueAsString(session)),
                    "expires_at", AttributeValue.fromN(String.valueOf(pastEpoch))))
            .build());

    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void updatePreservesExpiresAt() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));

    var before =
        dynamoDbClient
            .getItem(
                GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("session_id", AttributeValue.fromS(session.sessionId())))
                    .build())
            .item();
    String originalExpiresAt = before.get("expires_at").n();

    McpSession updated = session.withLogLevel(LoggingLevel.DEBUG);
    store.update(session.sessionId(), updated);

    assertThat(store.find(session.sessionId())).isPresent().hasValue(updated);

    var after =
        dynamoDbClient
            .getItem(
                GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("session_id", AttributeValue.fromS(session.sessionId())))
                    .build())
            .item();
    assertThat(after.get("expires_at").n()).isEqualTo(originalExpiresAt);
  }

  @Test
  void touchUpdatesOnlyExpiresAt() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofMinutes(5));

    var before =
        dynamoDbClient
            .getItem(
                GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("session_id", AttributeValue.fromS(session.sessionId())))
                    .build())
            .item();
    String originalPayload = before.get("payload").s();
    long originalExpiresAt = Long.parseLong(before.get("expires_at").n());

    store.touch(session.sessionId(), Duration.ofHours(2));

    var after =
        dynamoDbClient
            .getItem(
                GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of("session_id", AttributeValue.fromS(session.sessionId())))
                    .build())
            .item();
    assertThat(after.get("payload").s()).isEqualTo(originalPayload);
    assertThat(Long.parseLong(after.get("expires_at").n())).isGreaterThan(originalExpiresAt);
  }

  @Test
  void missingTableThrowsIllegalStateException() {
    deleteTable();
    var properties = new DynamoDbSessionStoreProperties();
    properties.setTableName("nonexistent_table");
    assertThatThrownBy(
            () -> new DynamoDbMcpSessionStore(dynamoDbClient, new JsonMapper(), properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not exist");
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
