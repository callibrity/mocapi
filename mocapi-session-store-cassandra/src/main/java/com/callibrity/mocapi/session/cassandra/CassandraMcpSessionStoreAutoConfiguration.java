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

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.session.McpSessionStore;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers a {@link CassandraMcpSessionStore} when a {@link CqlSession} is available. The
 * in-memory fallback in {@link MocapiAutoConfiguration} steps aside via its own
 * {@code @ConditionalOnMissingBean} once this backend bean is present.
 */
@AutoConfiguration(before = MocapiAutoConfiguration.class, after = CassandraAutoConfiguration.class)
@ConditionalOnClass(CqlSession.class)
@ConditionalOnBean(CqlSession.class)
public class CassandraMcpSessionStoreAutoConfiguration {

  @Bean
  public McpSessionStore cassandraMcpSessionStore(
      CqlSession cqlSession,
      ObjectMapper objectMapper,
      @Value("${mocapi.session.cassandra.keyspace:mocapi}") String keyspace,
      @Value("${mocapi.session.cassandra.table:mocapi_sessions}") String tableName,
      @Value("${mocapi.session.cassandra.read-consistency:LOCAL_ONE}") String readConsistencyStr,
      @Value("${mocapi.session.cassandra.write-consistency:LOCAL_QUORUM}")
          String writeConsistencyStr) {
    return new CassandraMcpSessionStore(
        cqlSession,
        objectMapper,
        keyspace,
        tableName,
        DefaultConsistencyLevel.valueOf(readConsistencyStr),
        DefaultConsistencyLevel.valueOf(writeConsistencyStr));
  }
}
