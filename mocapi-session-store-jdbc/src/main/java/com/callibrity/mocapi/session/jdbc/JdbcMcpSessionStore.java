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

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * JDBC-backed {@link McpSessionStore} using {@link JdbcClient}. Sessions are stored as JSON in a
 * single table with an {@code expires_at} column for TTL enforcement. The table name is validated
 * at construction time to prevent SQL injection.
 *
 * <p>TTL is enforced in two ways:
 *
 * <ol>
 *   <li><strong>At read time</strong> &mdash; {@link #find} filters out rows where {@code
 *       expires_at <= now()}.
 *   <li><strong>Periodic cleanup</strong> &mdash; a background task deletes expired rows at a
 *       configurable interval (default 5 minutes). Disable via {@code
 *       mocapi.session.jdbc.cleanup-enabled=false}.
 * </ol>
 *
 * <p>The expected schema can be initialized via {@code
 * spring.sql.init.schema-locations=classpath:schema/mocapi-session-store-postgresql.sql} (or the
 * appropriate dialect variant). Schema files for PostgreSQL, MySQL, and H2 are shipped in this
 * module's jar under {@code classpath:schema/}.
 */
public class JdbcMcpSessionStore implements McpSessionStore, AutoCloseable {

  static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final JdbcDialect dialect;
  private final String tableName;
  private final ScheduledExecutorService cleanupExecutor;

  JdbcMcpSessionStore(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      JdbcDialect dialect,
      String tableName,
      boolean cleanupEnabled,
      Duration cleanupInterval) {
    if (tableName == null || !SAFE_IDENTIFIER.matcher(tableName).matches()) {
      throw new IllegalArgumentException(
          "Invalid table name '"
              + tableName
              + "'. Table names must match "
              + SAFE_IDENTIFIER.pattern()
              + " — only letters, digits, and underscores are allowed,"
              + " and the name must not start with a digit.");
    }
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
    this.dialect = dialect;
    this.tableName = tableName;
    if (cleanupEnabled) {
      this.cleanupExecutor =
          Executors.newSingleThreadScheduledExecutor(
              r -> {
                Thread t = new Thread(r, "mocapi-session-cleanup");
                t.setDaemon(true);
                return t;
              });
      long intervalSeconds = cleanupInterval.toSeconds();
      cleanupExecutor.scheduleAtFixedRate(
          this::cleanupExpired, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    } else {
      this.cleanupExecutor = null;
    }
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    String json = objectMapper.writeValueAsString(session);
    Timestamp expiresAt = Timestamp.from(Instant.now().plus(ttl));
    jdbcClient
        .sql(dialect.upsertSql(tableName))
        .param("sid", session.sessionId())
        .param("payload", json)
        .param("expiresAt", expiresAt)
        .update();
  }

  @Override
  public void update(String sessionId, McpSession session) {
    String json = objectMapper.writeValueAsString(session);
    jdbcClient
        .sql("UPDATE " + tableName + " SET payload = :payload WHERE session_id = :sid")
        .param("payload", json)
        .param("sid", sessionId)
        .update();
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    return jdbcClient
        .sql("SELECT payload FROM " + tableName + " WHERE session_id = :sid AND expires_at > :now")
        .param("sid", sessionId)
        .param("now", Timestamp.from(Instant.now()))
        .query((rs, rowNum) -> objectMapper.readValue(rs.getString("payload"), McpSession.class))
        .optional();
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    Timestamp expiresAt = Timestamp.from(Instant.now().plus(ttl));
    jdbcClient
        .sql("UPDATE " + tableName + " SET expires_at = :expiresAt WHERE session_id = :sid")
        .param("expiresAt", expiresAt)
        .param("sid", sessionId)
        .update();
  }

  @Override
  public void delete(String sessionId) {
    jdbcClient
        .sql("DELETE FROM " + tableName + " WHERE session_id = :sid")
        .param("sid", sessionId)
        .update();
  }

  /** Deletes all rows whose {@code expires_at} is in the past. */
  public void cleanupExpired() {
    jdbcClient
        .sql("DELETE FROM " + tableName + " WHERE expires_at <= :now")
        .param("now", Timestamp.from(Instant.now()))
        .update();
  }

  @Override
  public void close() {
    if (cleanupExecutor != null) {
      cleanupExecutor.shutdown();
    }
  }
}
