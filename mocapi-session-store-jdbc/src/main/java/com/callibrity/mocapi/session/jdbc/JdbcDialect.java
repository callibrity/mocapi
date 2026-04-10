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

import java.sql.SQLException;
import javax.sql.DataSource;

/** Dialect-specific SQL for the JDBC session store. Only the upsert syntax varies by database. */
enum JdbcDialect {
  POSTGRESQL {
    @Override
    String upsertSql(String table) {
      return "INSERT INTO "
          + table
          + " (session_id, payload, expires_at) VALUES (:sid, :payload, :expiresAt)"
          + " ON CONFLICT (session_id) DO UPDATE"
          + " SET payload = EXCLUDED.payload, expires_at = EXCLUDED.expires_at";
    }
  },

  MYSQL {
    @Override
    String upsertSql(String table) {
      return "INSERT INTO "
          + table
          + " (session_id, payload, expires_at) VALUES (:sid, :payload, :expiresAt)"
          + " ON DUPLICATE KEY UPDATE payload = VALUES(payload), expires_at = VALUES(expires_at)";
    }
  },

  H2 {
    @Override
    String upsertSql(String table) {
      return "MERGE INTO "
          + table
          + " (session_id, payload, expires_at) KEY (session_id) VALUES (:sid, :payload, :expiresAt)";
    }
  };

  /**
   * Returns the dialect-specific upsert SQL for the given table. The SQL uses named parameters
   * {@code :sid}, {@code :payload}, and {@code :expiresAt}. <strong>The implementer may assume the
   * {@code tableName} is a validated bare SQL identifier</strong> &mdash; callers must validate
   * before invoking this method.
   */
  abstract String upsertSql(String tableName);

  static JdbcDialect detect(DataSource dataSource) {
    try (var connection = dataSource.getConnection()) {
      String productName = connection.getMetaData().getDatabaseProductName();
      return switch (productName) {
        case "PostgreSQL" -> POSTGRESQL;
        case "MySQL" -> MYSQL;
        case "H2" -> H2;
        default ->
            throw new IllegalArgumentException(
                "Unsupported database: "
                    + productName
                    + ". Supported: postgresql, mysql, h2."
                    + " Set mocapi.session.jdbc.dialect to override.");
      };
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to detect database dialect", e);
    }
  }

  static JdbcDialect from(String name) {
    return switch (name.toLowerCase()) {
      case "postgresql", "postgres" -> POSTGRESQL;
      case "mysql" -> MYSQL;
      case "h2" -> H2;
      default ->
          throw new IllegalArgumentException(
              "Unsupported dialect: " + name + ". Supported: postgresql, mysql, h2");
    };
  }
}
