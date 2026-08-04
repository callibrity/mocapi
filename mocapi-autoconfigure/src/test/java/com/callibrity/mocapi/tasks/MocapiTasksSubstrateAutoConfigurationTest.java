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
package com.callibrity.mocapi.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.mocapi.tasks.substrate.SubstrateTaskStore;
import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecAutoConfiguration;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.atom.InMemoryAtomSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class MocapiTasksSubstrateAutoConfigurationTest {

  @Test
  void fullAutoConfigurationChainActivatesSubstrateTaskStore() {
    new ApplicationContextRunner()
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .withConfiguration(
            AutoConfigurations.of(
                JacksonCodecAutoConfiguration.class,
                SubstrateOrderingAutoConfiguration.class,
                SubstrateAutoConfiguration.class,
                MocapiTasksSubstrateAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(TaskStore.class);
              assertThat(context.getBean(TaskStore.class)).isInstanceOf(SubstrateTaskStore.class);
            });
  }

  /**
   * Substrate 0.8.1 declares its own codec ordering, so the 0.8.0-era negative control (the same
   * chain minus the shim failing to produce a {@code TaskStore}) no longer fails and cannot prove
   * the shim load-bearing. Pin the shim's ordering metadata instead: an upstream class rename would
   * silently turn {@link SubstrateOrderingAutoConfiguration} into a no-op for 0.8.0-pinned
   * applications, and this assertion turns that into a test failure.
   */
  @Test
  void orderingShimPinsSubstrateAfterEveryCodecAutoConfiguration() {
    AutoConfiguration annotation =
        SubstrateOrderingAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.beforeName())
        .containsExactly("org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration");
    assertThat(annotation.afterName())
        .containsExactlyInAnyOrder(
            "org.jwcarman.codec.jackson.JacksonCodecAutoConfiguration",
            "org.jwcarman.codec.gson.GsonCodecAutoConfiguration",
            "org.jwcarman.codec.protobuf.ProtobufCodecAutoConfiguration");
  }

  @Test
  void registersSubstrateTaskStoreWhenAtomFactoryPresent() {
    new ApplicationContextRunner()
        .withBean(AtomFactory.class, MocapiTasksSubstrateAutoConfigurationTest::inMemoryAtomFactory)
        .withConfiguration(AutoConfigurations.of(MocapiTasksSubstrateAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(TaskStore.class);
              assertThat(context.getBean(TaskStore.class)).isInstanceOf(SubstrateTaskStore.class);
            });
  }

  @Test
  void backsOffWhenUserSuppliesTaskStore() {
    TaskStore custom = new InMemoryStub();
    new ApplicationContextRunner()
        .withBean(AtomFactory.class, MocapiTasksSubstrateAutoConfigurationTest::inMemoryAtomFactory)
        .withBean("customTaskStore", TaskStore.class, () -> custom)
        .withConfiguration(AutoConfigurations.of(MocapiTasksSubstrateAutoConfiguration.class))
        .run(context -> assertThat(context.getBean(TaskStore.class)).isSameAs(custom));
  }

  @Test
  void backsOffWithoutAtomFactory() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MocapiTasksSubstrateAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(TaskStore.class));
  }

  @Test
  void keyPrefixPropertyIsApplied() {
    new ApplicationContextRunner()
        .withBean(AtomFactory.class, MocapiTasksSubstrateAutoConfigurationTest::inMemoryAtomFactory)
        .withPropertyValues("mocapi.tasks.substrate.key-prefix=acme:jobs:")
        .withConfiguration(AutoConfigurations.of(MocapiTasksSubstrateAutoConfiguration.class))
        .run(
            context ->
                assertThat(context.getBean(MocapiTasksSubstrateProperties.class).keyPrefix())
                    .isEqualTo("acme:jobs:"));
  }

  private static AtomFactory inMemoryAtomFactory() {
    CodecFactory codecFactory = new JacksonCodecFactory(JsonMapper.builder().build());
    return new DefaultAtomFactory(
        new InMemoryAtomSpi(),
        codecFactory,
        PayloadTransformer.IDENTITY,
        new DefaultNotifier(new InMemoryNotifier(), codecFactory),
        Duration.ofDays(30),
        new ShutdownCoordinator());
  }

  private static final class InMemoryStub implements TaskStore {
    @Override
    public void create(TaskRecord rec) {
      // intentional no-op: the stub exists only to occupy the TaskStore bean slot
    }

    @Override
    public Optional<TaskRecord> get(String taskId) {
      return Optional.empty();
    }

    @Override
    public Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation) {
      return Optional.empty();
    }

    @Override
    public void delete(String taskId) {
      // intentional no-op: the stub exists only to occupy the TaskStore bean slot
    }
  }
}
