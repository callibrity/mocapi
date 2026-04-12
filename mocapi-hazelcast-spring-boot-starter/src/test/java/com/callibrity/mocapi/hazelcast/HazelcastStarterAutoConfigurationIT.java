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

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.core.atom.AtomSpi;
import org.jwcarman.substrate.core.journal.JournalSpi;
import org.jwcarman.substrate.core.mailbox.MailboxSpi;
import org.jwcarman.substrate.core.notifier.NotifierSpi;
import org.jwcarman.substrate.hazelcast.HazelcastAutoConfiguration;
import org.jwcarman.substrate.hazelcast.atom.HazelcastAtomAutoConfiguration;
import org.jwcarman.substrate.hazelcast.atom.HazelcastAtomSpi;
import org.jwcarman.substrate.hazelcast.journal.HazelcastJournalAutoConfiguration;
import org.jwcarman.substrate.hazelcast.journal.HazelcastJournalSpi;
import org.jwcarman.substrate.hazelcast.mailbox.HazelcastMailboxAutoConfiguration;
import org.jwcarman.substrate.hazelcast.mailbox.HazelcastMailboxSpi;
import org.jwcarman.substrate.hazelcast.notifier.HazelcastNotifierAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies that pulling the mocapi-hazelcast-spring-boot-starter on the classpath (with a real
 * embedded Hazelcast instance) produces the full distributed stack: Substrate's Hazelcast backend
 * provides all four SPIs (Atom, Mailbox, Journal, Notifier), which the session store adapter and
 * stream context use via the {@code AtomFactory} and other factory beans.
 */
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
                HazelcastAutoConfiguration.class,
                HazelcastAtomAutoConfiguration.class,
                HazelcastMailboxAutoConfiguration.class,
                HazelcastJournalAutoConfiguration.class,
                HazelcastNotifierAutoConfiguration.class));
  }

  @Test
  void hazelcastBackedAtomSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(AtomSpi.class);
              assertThat(context.getBean(AtomSpi.class)).isInstanceOf(HazelcastAtomSpi.class);
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
  void hazelcastBackedNotifierSpiIsAutoConfigured() {
    contextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(NotifierSpi.class);
            });
  }
}
