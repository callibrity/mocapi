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

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * DynamoDB-backed {@link McpSessionStore}. Sessions are stored as JSON in a single table.
 * DynamoDB's native TTL on the {@code expires_at} attribute handles eventual cleanup, while reads
 * filter expired items at query time.
 */
public class DynamoDbMcpSessionStore implements McpSessionStore {

  private final DynamoDbClient dynamoDbClient;
  private final ObjectMapper objectMapper;
  private final String tableName;

  DynamoDbMcpSessionStore(
      DynamoDbClient dynamoDbClient,
      ObjectMapper objectMapper,
      DynamoDbSessionStoreProperties properties) {
    this.dynamoDbClient = dynamoDbClient;
    this.objectMapper = objectMapper;
    this.tableName = properties.getTableName();
    if (properties.isVerifyTable()) {
      verifyTableExists();
    }
    if (properties.isEnsureTtl()) {
      ensureTtlEnabled();
    }
  }

  private void verifyTableExists() {
    try {
      dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
    } catch (ResourceNotFoundException e) {
      throw new IllegalStateException(
          "DynamoDB table '"
              + tableName
              + "' does not exist. Create it via your "
              + "infrastructure-as-code tool (CloudFormation, Terraform, CDK) before "
              + "starting the application. See the module's README for the expected schema.",
          e);
    }
  }

  private void ensureTtlEnabled() {
    var description =
        dynamoDbClient
            .describeTimeToLive(DescribeTimeToLiveRequest.builder().tableName(tableName).build())
            .timeToLiveDescription();
    if (description.timeToLiveStatus() != TimeToLiveStatus.ENABLED) {
      dynamoDbClient.updateTimeToLive(
          UpdateTimeToLiveRequest.builder()
              .tableName(tableName)
              .timeToLiveSpecification(
                  TimeToLiveSpecification.builder()
                      .enabled(true)
                      .attributeName("expires_at")
                      .build())
              .build());
    }
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    long expiresAt = Instant.now().plus(ttl).getEpochSecond();
    String json = objectMapper.writeValueAsString(session);
    dynamoDbClient.putItem(
        PutItemRequest.builder()
            .tableName(tableName)
            .item(
                Map.of(
                    "session_id", AttributeValue.fromS(session.sessionId()),
                    "payload", AttributeValue.fromS(json),
                    "expires_at", AttributeValue.fromN(String.valueOf(expiresAt))))
            .build());
  }

  @Override
  public void update(String sessionId, McpSession session) {
    String json = objectMapper.writeValueAsString(session);
    dynamoDbClient.updateItem(
        UpdateItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
            .updateExpression("SET payload = :p")
            .expressionAttributeValues(Map.of(":p", AttributeValue.fromS(json)))
            .build());
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    GetItemResponse response =
        dynamoDbClient.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
                .consistentRead(true)
                .build());
    if (!response.hasItem()) {
      return Optional.empty();
    }
    var item = response.item();
    long expiresAt = Long.parseLong(item.get("expires_at").n());
    if (expiresAt <= Instant.now().getEpochSecond()) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(item.get("payload").s(), McpSession.class));
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    long expiresAt = Instant.now().plus(ttl).getEpochSecond();
    dynamoDbClient.updateItem(
        UpdateItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
            .updateExpression("SET expires_at = :e")
            .expressionAttributeValues(
                Map.of(":e", AttributeValue.fromN(String.valueOf(expiresAt))))
            .build());
  }

  @Override
  public void delete(String sessionId) {
    dynamoDbClient.deleteItem(
        DeleteItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
            .build());
  }
}
