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

import com.callibrity.mocapi.session.McpSessionStore;
import com.callibrity.mocapi.session.redis.RedisMcpSessionStore;
import com.callibrity.mocapi.session.redis.RedisMcpSessionStoreAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.journal.redis.RedisJournalAutoConfiguration;
import org.jwcarman.substrate.journal.redis.RedisJournalSpi;
import org.jwcarman.substrate.mailbox.redis.RedisMailboxAutoConfiguration;
import org.jwcarman.substrate.mailbox.redis.RedisMailboxSpi;
import org.jwcarman.substrate.notifier.redis.RedisNotifier;
import org.jwcarman.substrate.notifier.redis.RedisNotifierAutoConfiguration;
import org.jwcarman.substrate.spi.JournalSpi;
import org.jwcarman.substrate.spi.MailboxSpi;
import org.jwcarman.substrate.spi.Notifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RedisStarterAutoConfigurationIT {

  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @BeforeAll
  static void startRedis() {
    redis.start();
  }

  @AfterAll
  static void stopRedis() {
    redis.stop();
  }

  @Configuration(proxyBeanMethods = false)
  static class RedisInfrastructureConfig {

    @Bean
    LettuceConnectionFactory redisConnectionFactory() {
      return new LettuceConnectionFactory(
          new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
    }

    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(RedisInfrastructureConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                DataRedisAutoConfiguration.class,
                RedisMcpSessionStoreAutoConfiguration.class,
                RedisMailboxAutoConfiguration.class,
                RedisJournalAutoConfiguration.class,
                RedisNotifierAutoConfiguration.class));
  }

  @Test
  void redisBackedSessionStoreIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(RedisMcpSessionStore.class);
            });
  }

  @Test
  void redisBackedMailboxSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(MailboxSpi.class);
              assertThat(context.getBean(MailboxSpi.class)).isInstanceOf(RedisMailboxSpi.class);
            });
  }

  @Test
  void redisBackedJournalSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(JournalSpi.class);
              assertThat(context.getBean(JournalSpi.class)).isInstanceOf(RedisJournalSpi.class);
            });
  }

  @Test
  void redisBackedNotifierIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(Notifier.class);
              assertThat(context.getBean(Notifier.class)).isInstanceOf(RedisNotifier.class);
            });
  }
}
