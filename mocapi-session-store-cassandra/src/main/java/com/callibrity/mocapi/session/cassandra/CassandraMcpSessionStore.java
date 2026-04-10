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

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import java.time.Duration;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/**
 * Cassandra-backed {@link McpSessionStore}. Sessions are stored as JSON in a single table using
 * Cassandra's native per-row TTL for automatic expiration — no cleanup task required.
 *
 * <p>The expected schema can be initialized via {@code cqlsh} using the CQL script shipped at
 * {@code classpath:schema/mocapi-session-store-cassandra.cql}. Operators must create the keyspace
 * separately.
 */
public class CassandraMcpSessionStore implements McpSessionStore {

  private final CqlSession cqlSession;
  private final ObjectMapper objectMapper;
  private final ConsistencyLevel readConsistency;
  private final ConsistencyLevel writeConsistency;
  private final PreparedStatement saveStatement;
  private final PreparedStatement findStatement;
  private final PreparedStatement ttlStatement;
  private final PreparedStatement updateStatement;
  private final PreparedStatement deleteStatement;

  CassandraMcpSessionStore(
      CqlSession cqlSession,
      ObjectMapper objectMapper,
      String keyspace,
      String tableName,
      ConsistencyLevel readConsistency,
      ConsistencyLevel writeConsistency) {
    this.cqlSession = cqlSession;
    this.objectMapper = objectMapper;
    this.readConsistency = readConsistency;
    this.writeConsistency = writeConsistency;
    String qualifiedTable = keyspace + "." + tableName;
    this.saveStatement =
        cqlSession.prepare(
            "INSERT INTO " + qualifiedTable + " (session_id, payload) VALUES (?, ?) USING TTL ?");
    this.findStatement =
        cqlSession.prepare("SELECT payload FROM " + qualifiedTable + " WHERE session_id = ?");
    this.ttlStatement =
        cqlSession.prepare("SELECT TTL(payload) FROM " + qualifiedTable + " WHERE session_id = ?");
    this.updateStatement =
        cqlSession.prepare(
            "UPDATE " + qualifiedTable + " USING TTL ? SET payload = ? WHERE session_id = ?");
    this.deleteStatement =
        cqlSession.prepare("DELETE FROM " + qualifiedTable + " WHERE session_id = ?");
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    String json = objectMapper.writeValueAsString(session);
    cqlSession.execute(
        saveStatement
            .bind(session.sessionId(), json, (int) ttl.toSeconds())
            .setConsistencyLevel(writeConsistency));
  }

  @Override
  public void update(String sessionId, McpSession session) {
    Row ttlRow =
        cqlSession.execute(ttlStatement.bind(sessionId).setConsistencyLevel(readConsistency)).one();
    int remainingTtl = (ttlRow != null && !ttlRow.isNull(0)) ? ttlRow.getInt(0) : 0;
    String json = objectMapper.writeValueAsString(session);
    cqlSession.execute(
        updateStatement.bind(remainingTtl, json, sessionId).setConsistencyLevel(writeConsistency));
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    Row row =
        cqlSession
            .execute(findStatement.bind(sessionId).setConsistencyLevel(readConsistency))
            .one();
    if (row == null) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(row.getString("payload"), McpSession.class));
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    Row row =
        cqlSession
            .execute(findStatement.bind(sessionId).setConsistencyLevel(readConsistency))
            .one();
    if (row == null) {
      return;
    }
    String currentPayload = row.getString("payload");
    cqlSession.execute(
        updateStatement
            .bind((int) ttl.toSeconds(), currentPayload, sessionId)
            .setConsistencyLevel(writeConsistency));
  }

  @Override
  public void delete(String sessionId) {
    cqlSession.execute(deleteStatement.bind(sessionId).setConsistencyLevel(writeConsistency));
  }
}
