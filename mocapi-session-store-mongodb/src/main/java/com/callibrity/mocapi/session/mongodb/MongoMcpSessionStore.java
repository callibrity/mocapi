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

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import com.mongodb.client.model.ReplaceOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import tools.jackson.databind.ObjectMapper;

/**
 * MongoDB-backed {@link McpSessionStore}. Sessions are stored as JSON in a single collection using
 * MongoDB's TTL index on the {@code expiresAt} field for automatic expiration.
 */
public class MongoMcpSessionStore implements McpSessionStore {

  private final MongoTemplate mongoTemplate;
  private final ObjectMapper objectMapper;
  private final String collectionName;

  MongoMcpSessionStore(
      MongoTemplate mongoTemplate, ObjectMapper objectMapper, String collectionName) {
    this.mongoTemplate = mongoTemplate;
    this.objectMapper = objectMapper;
    this.collectionName = collectionName;
    mongoTemplate
        .indexOps(collectionName)
        .ensureIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(0, TimeUnit.SECONDS));
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    String json = objectMapper.writeValueAsString(session);
    var doc =
        new Document("_id", session.sessionId())
            .append("payload", json)
            .append("expiresAt", Date.from(Instant.now().plus(ttl)));
    mongoTemplate
        .getCollection(collectionName)
        .replaceOne(
            new Document("_id", session.sessionId()), doc, new ReplaceOptions().upsert(true));
  }

  @Override
  public void update(String sessionId, McpSession session) {
    String json = objectMapper.writeValueAsString(session);
    mongoTemplate
        .getCollection(collectionName)
        .updateOne(
            new Document("_id", sessionId), new Document("$set", new Document("payload", json)));
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    var doc =
        mongoTemplate
            .getCollection(collectionName)
            .find(
                new Document("_id", sessionId).append("expiresAt", new Document("$gt", new Date())))
            .first();
    if (doc == null) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(doc.getString("payload"), McpSession.class));
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    mongoTemplate
        .getCollection(collectionName)
        .updateOne(
            new Document("_id", sessionId),
            new Document("$set", new Document("expiresAt", Date.from(Instant.now().plus(ttl)))));
  }

  @Override
  public void delete(String sessionId) {
    mongoTemplate.getCollection(collectionName).deleteOne(new Document("_id", sessionId));
  }
}
