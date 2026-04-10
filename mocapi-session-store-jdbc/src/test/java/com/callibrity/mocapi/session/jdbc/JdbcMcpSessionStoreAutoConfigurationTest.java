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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.session.McpSessionStore;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class JdbcMcpSessionStoreAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JdbcMcpSessionStoreAutoConfiguration.class));

  private static DataSource h2DataSource() {
    return new DriverManagerDataSource(
        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
  }

  @Test
  void registersJdbcStoreWhenDataSourceIsAvailable() {
    DataSource dataSource = h2DataSource();
    runner
        .withPropertyValues("mocapi.session.jdbc.cleanup-enabled=false")
        .withBean(DataSource.class, () -> dataSource)
        .withBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource))
        .withBean(ObjectMapper.class, JsonMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(JdbcMcpSessionStore.class);
            });
  }

  @Test
  void userProvidedBeanTakesPrecedence() {
    DataSource dataSource = h2DataSource();
    McpSessionStore userStore = mock(McpSessionStore.class);
    runner
        .withPropertyValues("mocapi.session.jdbc.cleanup-enabled=false")
        .withBean(DataSource.class, () -> dataSource)
        .withBean(JdbcTemplate.class, () -> new JdbcTemplate(dataSource))
        .withBean(ObjectMapper.class, JsonMapper::new)
        .withBean(McpSessionStore.class, () -> userStore)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class)).isSameAs(userStore);
            });
  }

  @Test
  void doesNotRegisterWithoutDataSource() {
    runner.run(context -> assertThat(context).doesNotHaveBean(McpSessionStore.class));
  }
}
