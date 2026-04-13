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

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.core.atom.AtomSpi;
import org.jwcarman.substrate.core.journal.JournalSpi;
import org.jwcarman.substrate.core.mailbox.MailboxSpi;
import org.jwcarman.substrate.core.notifier.NotifierSpi;
import org.jwcarman.substrate.postgresql.PostgresAutoConfiguration;
import org.jwcarman.substrate.postgresql.atom.PostgresAtomAutoConfiguration;
import org.jwcarman.substrate.postgresql.atom.PostgresAtomSpi;
import org.jwcarman.substrate.postgresql.journal.PostgresJournalAutoConfiguration;
import org.jwcarman.substrate.postgresql.journal.PostgresJournalSpi;
import org.jwcarman.substrate.postgresql.mailbox.PostgresMailboxAutoConfiguration;
import org.jwcarman.substrate.postgresql.mailbox.PostgresMailboxSpi;
import org.jwcarman.substrate.postgresql.notifier.PostgresNotifierAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that Substrate's Postgres backend auto-configures all four SPIs with a real PostgreSQL.
 */
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
            "substrate.postgresql.atom.auto-create-schema=true",
            "substrate.postgresql.mailbox.auto-create-schema=true",
            "substrate.postgresql.journal.auto-create-schema=true")
        .withConfiguration(
            AutoConfigurations.of(
                JdbcTemplateAutoConfiguration.class,
                JdbcClientAutoConfiguration.class,
                PostgresAutoConfiguration.class,
                PostgresAtomAutoConfiguration.class,
                PostgresMailboxAutoConfiguration.class,
                PostgresJournalAutoConfiguration.class,
                PostgresNotifierAutoConfiguration.class));
  }

  @Test
  void postgresBackedAtomSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(AtomSpi.class);
              assertThat(context.getBean(AtomSpi.class)).isInstanceOf(PostgresAtomSpi.class);
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
  void postgresBackedNotifierSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotifierSpi.class);
            });
  }
}
