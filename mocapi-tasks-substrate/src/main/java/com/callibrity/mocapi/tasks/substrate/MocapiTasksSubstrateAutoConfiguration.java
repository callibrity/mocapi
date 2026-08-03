/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.tasks.substrate;

import com.callibrity.mocapi.tasks.MocapiTasksAutoConfiguration;
import com.callibrity.mocapi.tasks.engine.TaskExecutionEngine;
import com.callibrity.mocapi.tasks.store.TaskStore;
import java.time.Clock;
import org.jwcarman.substrate.atom.AtomFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-activates a {@link SubstrateTaskStore} when a Substrate {@link AtomFactory} bean is present,
 * replacing the in-memory default that {@link MocapiTasksAutoConfiguration} would otherwise
 * register ({@code before =} ensures this store wins; a user-defined {@link TaskStore} bean still
 * beats both). Runs after Substrate's own autoconfiguration (referenced by name to keep {@code
 * substrate-core} off the compile classpath) so the {@link AtomFactory} bean is registered before
 * the {@link ConditionalOnBean} condition is evaluated.
 */
@AutoConfiguration(
    before = MocapiTasksAutoConfiguration.class,
    afterName = "org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration")
@ConditionalOnClass({AtomFactory.class, TaskExecutionEngine.class})
@EnableConfigurationProperties(MocapiTasksSubstrateProperties.class)
public class MocapiTasksSubstrateAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(MocapiTasksSubstrateAutoConfiguration.class);

  /** Mirrors the Clock default in {@code MocapiTasksAutoConfiguration}, which runs after us. */
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock mcpTasksClock() {
    return Clock.systemUTC();
  }

  /**
   * The Substrate-backed store. Shared and durable: safe for multi-node deployments, and in-flight
   * tasks survive a restart (subject to each record's TTL).
   */
  @Bean
  @ConditionalOnBean(AtomFactory.class)
  @ConditionalOnMissingBean(TaskStore.class)
  public SubstrateTaskStore mcpTaskStore(
      AtomFactory atomFactory, Clock clock, MocapiTasksSubstrateProperties properties) {
    log.info(
        "Using the Substrate-backed TaskStore (key prefix '{}'): task state is shared across "
            + "nodes and survives restarts.",
        properties.keyPrefix());
    return new SubstrateTaskStore(atomFactory, clock, properties.keyPrefix());
  }
}
