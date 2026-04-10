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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.session.McpSessionStore;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class CassandraMcpSessionStoreAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(CassandraMcpSessionStoreAutoConfiguration.class));

  private static CqlSession mockCqlSession() {
    CqlSession session = mock(CqlSession.class);
    when(session.prepare(anyString())).thenReturn(mock(PreparedStatement.class));
    return session;
  }

  @Test
  void registersCassandraStoreWhenCqlSessionIsAvailable() {
    runner
        .withBean(CqlSession.class, CassandraMcpSessionStoreAutoConfigurationTest::mockCqlSession)
        .withBean(ObjectMapper.class, JsonMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(CassandraMcpSessionStore.class);
            });
  }

  @Test
  void userProvidedBeanTakesPrecedence() {
    McpSessionStore userStore = mock(McpSessionStore.class);
    runner
        .withBean(CqlSession.class, CassandraMcpSessionStoreAutoConfigurationTest::mockCqlSession)
        .withBean(ObjectMapper.class, JsonMapper::new)
        .withBean(McpSessionStore.class, () -> userStore)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class)).isSameAs(userStore);
            });
  }

  @Test
  void doesNotRegisterWithoutCqlSession() {
    runner.run(context -> assertThat(context).doesNotHaveBean(McpSessionStore.class));
  }
}
