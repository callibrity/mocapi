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
package com.callibrity.mocapi.postgresql;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.session.McpSessionStore;
import com.callibrity.mocapi.session.jdbc.JdbcMcpSessionStore;
import com.callibrity.mocapi.session.jdbc.JdbcMcpSessionStoreAutoConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.journal.postgresql.PostgresJournalAutoConfiguration;
import org.jwcarman.substrate.journal.postgresql.PostgresJournalSpi;
import org.jwcarman.substrate.mailbox.postgresql.PostgresMailboxAutoConfiguration;
import org.jwcarman.substrate.mailbox.postgresql.PostgresMailboxSpi;
import org.jwcarman.substrate.notifier.postgresql.PostgresNotifier;
import org.jwcarman.substrate.notifier.postgresql.PostgresNotifierAutoConfiguration;
import org.jwcarman.substrate.spi.JournalSpi;
import org.jwcarman.substrate.spi.MailboxSpi;
import org.jwcarman.substrate.spi.Notifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class PostgresqlStarterAutoConfigurationIT {

  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("mocapi");

  @BeforeAll
  static void startPostgres() {
    postgres.start();
  }

  @AfterAll
  static void stopPostgres() {
    postgres.stop();
  }

  @Configuration(proxyBeanMethods = false)
  static class PostgresInfrastructureConfig {

    @Bean
    DataSource dataSource() {
      var ds = new DriverManagerDataSource();
      ds.setDriverClassName("org.postgresql.Driver");
      ds.setUrl(postgres.getJdbcUrl());
      ds.setUsername(postgres.getUsername());
      ds.setPassword(postgres.getPassword());

      var populator = new ResourceDatabasePopulator();
      populator.addScript(new ClassPathResource("schema/mocapi-session-store-postgresql.sql"));
      populator.execute(ds);

      return ds;
    }

    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(PostgresInfrastructureConfig.class)
        .withPropertyValues(
            "substrate.mailbox.postgresql.auto-create-schema=true",
            "substrate.journal.postgresql.auto-create-schema=true")
        .withConfiguration(
            AutoConfigurations.of(
                JdbcTemplateAutoConfiguration.class,
                JdbcClientAutoConfiguration.class,
                JdbcMcpSessionStoreAutoConfiguration.class,
                PostgresMailboxAutoConfiguration.class,
                PostgresJournalAutoConfiguration.class,
                PostgresNotifierAutoConfiguration.class));
  }

  @Test
  void jdbcBackedSessionStoreIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(JdbcMcpSessionStore.class);
            });
  }

  @Test
  void postgresBackedMailboxSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(MailboxSpi.class);
              assertThat(context.getBean(MailboxSpi.class)).isInstanceOf(PostgresMailboxSpi.class);
            });
  }

  @Test
  void postgresBackedJournalSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(JournalSpi.class);
              assertThat(context.getBean(JournalSpi.class)).isInstanceOf(PostgresJournalSpi.class);
            });
  }

  @Test
  void postgresBackedNotifierIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(Notifier.class);
              assertThat(context.getBean(Notifier.class)).isInstanceOf(PostgresNotifier.class);
            });
  }
}
