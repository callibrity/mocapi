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

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.session.McpSessionStore;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers a {@link JdbcMcpSessionStore} when a {@link DataSource} and {@link JdbcClient} are
 * available and no other store is defined.
 */
@AutoConfiguration(
    before = MocapiAutoConfiguration.class,
    afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBean({DataSource.class, JdbcClient.class})
public class JdbcMcpSessionStoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(McpSessionStore.class)
  public McpSessionStore jdbcMcpSessionStore(
      JdbcClient jdbcClient,
      DataSource dataSource,
      ObjectMapper objectMapper,
      @Value("${mocapi.session.jdbc.table-name:mocapi_sessions}") String tableName,
      @Value("${mocapi.session.jdbc.dialect:}") String dialectOverride,
      @Value("${mocapi.session.jdbc.cleanup-enabled:true}") boolean cleanupEnabled,
      @Value("${mocapi.session.jdbc.cleanup-interval:PT5M}") String cleanupIntervalStr) {
    JdbcDialect dialect =
        dialectOverride.isEmpty()
            ? JdbcDialect.detect(dataSource)
            : JdbcDialect.from(dialectOverride);
    Duration cleanupInterval = Duration.parse(cleanupIntervalStr);
    return new JdbcMcpSessionStore(
        jdbcClient, objectMapper, dialect, tableName, cleanupEnabled, cleanupInterval);
  }
}
