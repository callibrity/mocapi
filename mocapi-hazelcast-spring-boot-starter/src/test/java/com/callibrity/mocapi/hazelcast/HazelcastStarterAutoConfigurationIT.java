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
package com.callibrity.mocapi.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.session.McpSessionStore;
import com.callibrity.mocapi.session.hazelcast.HazelcastMcpSessionStore;
import com.callibrity.mocapi.session.hazelcast.HazelcastMcpSessionStoreAutoConfiguration;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.journal.hazelcast.HazelcastJournalAutoConfiguration;
import org.jwcarman.substrate.journal.hazelcast.HazelcastJournalSpi;
import org.jwcarman.substrate.mailbox.hazelcast.HazelcastMailboxAutoConfiguration;
import org.jwcarman.substrate.mailbox.hazelcast.HazelcastMailboxSpi;
import org.jwcarman.substrate.notifier.hazelcast.HazelcastNotifier;
import org.jwcarman.substrate.notifier.hazelcast.HazelcastNotifierAutoConfiguration;
import org.jwcarman.substrate.spi.JournalSpi;
import org.jwcarman.substrate.spi.MailboxSpi;
import org.jwcarman.substrate.spi.Notifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class HazelcastStarterAutoConfigurationIT {

  static HazelcastInstance hazelcastInstance;

  @BeforeAll
  static void startHazelcast() {
    Config config = new Config();
    config.setClusterName("mocapi-test");
    config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
    hazelcastInstance = Hazelcast.newHazelcastInstance(config);
  }

  @AfterAll
  static void stopHazelcast() {
    if (hazelcastInstance != null) {
      hazelcastInstance.shutdown();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class HazelcastInfrastructureConfig {

    @Bean(destroyMethod = "")
    HazelcastInstance hazelcastInstance() {
      return hazelcastInstance;
    }

    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(HazelcastInfrastructureConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                HazelcastMcpSessionStoreAutoConfiguration.class,
                HazelcastMailboxAutoConfiguration.class,
                HazelcastJournalAutoConfiguration.class,
                HazelcastNotifierAutoConfiguration.class));
  }

  @Test
  void hazelcastBackedSessionStoreIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(HazelcastMcpSessionStore.class);
            });
  }

  @Test
  void hazelcastBackedMailboxSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(MailboxSpi.class);
              assertThat(context.getBean(MailboxSpi.class)).isInstanceOf(HazelcastMailboxSpi.class);
            });
  }

  @Test
  void hazelcastBackedJournalSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(JournalSpi.class);
              assertThat(context.getBean(JournalSpi.class)).isInstanceOf(HazelcastJournalSpi.class);
            });
  }

  @Test
  void hazelcastBackedNotifierIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(Notifier.class);
              assertThat(context.getBean(Notifier.class)).isInstanceOf(HazelcastNotifier.class);
            });
  }
}
