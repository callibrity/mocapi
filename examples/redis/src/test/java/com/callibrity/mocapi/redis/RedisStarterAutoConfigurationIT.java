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
package com.callibrity.mocapi.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.core.atom.AtomSpi;
import org.jwcarman.substrate.core.journal.JournalSpi;
import org.jwcarman.substrate.core.mailbox.MailboxSpi;
import org.jwcarman.substrate.core.notifier.NotifierSpi;
import org.jwcarman.substrate.redis.RedisAutoConfiguration;
import org.jwcarman.substrate.redis.atom.RedisAtomAutoConfiguration;
import org.jwcarman.substrate.redis.atom.RedisAtomSpi;
import org.jwcarman.substrate.redis.journal.RedisJournalAutoConfiguration;
import org.jwcarman.substrate.redis.journal.RedisJournalSpi;
import org.jwcarman.substrate.redis.mailbox.RedisMailboxAutoConfiguration;
import org.jwcarman.substrate.redis.mailbox.RedisMailboxSpi;
import org.jwcarman.substrate.redis.notifier.RedisNotifierAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;

/** Verifies that Substrate's Redis backend auto-configures all four SPIs with a real Redis. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RedisStarterAutoConfigurationIT {

  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @BeforeAll
  static void start_redis() {
    redis.start();
  }

  @AfterAll
  static void stop_redis() {
    redis.stop();
  }

  @Configuration(proxyBeanMethods = false)
  static class RedisInfrastructureConfig {

    @Bean
    LettuceConnectionFactory redisConnectionFactory() {
      return new LettuceConnectionFactory(
          new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
    }
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(RedisInfrastructureConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                DataRedisAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisAtomAutoConfiguration.class,
                RedisMailboxAutoConfiguration.class,
                RedisJournalAutoConfiguration.class,
                RedisNotifierAutoConfiguration.class));
  }

  @Test
  void redis_backed_atom_spi_is_auto_configured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(AtomSpi.class);
              assertThat(context.getBean(AtomSpi.class)).isInstanceOf(RedisAtomSpi.class);
            });
  }

  @Test
  void redis_backed_mailbox_spi_is_auto_configured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(MailboxSpi.class);
              assertThat(context.getBean(MailboxSpi.class)).isInstanceOf(RedisMailboxSpi.class);
            });
  }

  @Test
  void redis_backed_journal_spi_is_auto_configured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(JournalSpi.class);
              assertThat(context.getBean(JournalSpi.class)).isInstanceOf(RedisJournalSpi.class);
            });
  }

  @Test
  void redis_backed_notifier_spi_is_auto_configured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotifierSpi.class);
            });
  }
}
