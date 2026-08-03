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

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.mocapi.tasks.store.TaskStoreContractTest;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import org.jwcarman.substrate.redis.atom.RedisAtomSpi;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the full {@link TaskStoreContractTest} TCK against a real Redis via Substrate's {@link
 * RedisAtomSpi}. Each {@code newStore} call gets a unique SPI key prefix because the TCK reuses
 * task ids across tests and Redis state is shared for the whole class.
 */
class RedisSubstrateTaskStoreIT extends TaskStoreContractTest {

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private static RedisClient client;
  private static RedisCommands<String, String> commands;

  @BeforeAll
  static void startRedis() {
    REDIS.start();
    client =
        RedisClient.create(
            RedisURI.builder()
                .withHost(REDIS.getHost())
                .withPort(REDIS.getFirstMappedPort())
                .build());
    commands = client.connect(StringCodec.UTF8).sync();
  }

  @AfterAll
  static void stopRedis() {
    if (client != null) {
      client.shutdown();
    }
    REDIS.stop();
  }

  @Override
  protected TaskStore newStore(Clock clock) {
    return storeOverPrefix(clock, "tck:" + UUID.randomUUID() + ":");
  }

  private static TaskStore storeOverPrefix(Clock clock, String spiPrefix) {
    CodecFactory codecFactory = new JacksonCodecFactory(JsonMapper.builder().build());
    AtomFactory atomFactory =
        new DefaultAtomFactory(
            new RedisAtomSpi(commands, spiPrefix),
            codecFactory,
            PayloadTransformer.IDENTITY,
            new DefaultNotifier(new InMemoryNotifier(), codecFactory),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    return new SubstrateTaskStore(atomFactory, clock, "mocapi:tasks:");
  }

  /**
   * Proves the module's core claim: two {@link SubstrateTaskStore} instances built over the same
   * SPI key prefix share state through Redis, rather than each holding its own private view. Every
   * other test in this class and in {@link TaskStoreContractTest} mints a unique prefix per store,
   * so none of them would catch a regression where {@code SubstrateTaskStore} accidentally cached
   * records locally instead of round-tripping through the shared backend.
   */
  @Test
  void two_stores_over_the_same_prefix_share_state_across_nodes() {
    String sharedPrefix = "shared:" + UUID.randomUUID() + ":";
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);
    TaskStore storeA = storeOverPrefix(clock, sharedPrefix);
    TaskStore storeB = storeOverPrefix(clock, sharedPrefix);
    String taskId = "cross-node-" + UUID.randomUUID();
    TaskRecord created =
        new TaskRecord(
            taskId,
            "demo.tool",
            null,
            "user-1",
            "2026-07-28",
            null,
            TaskStatus.WORKING,
            "0",
            clock.instant(),
            clock.instant(),
            Duration.ofMinutes(5),
            Duration.ofSeconds(1),
            List.of(),
            Map.of(),
            null,
            null,
            0L);

    storeA.create(created);

    Optional<TaskRecord> seenByB = storeB.get(taskId);
    assertThat(seenByB).contains(created);

    Optional<TaskRecord> updated =
        storeB.update(taskId, r -> r.withStatusMessage("halfway", clock.instant()));
    assertThat(updated).isPresent();
    assertThat(updated.orElseThrow().version()).isEqualTo(created.version() + 1);

    Optional<TaskRecord> seenByA = storeA.get(taskId);
    assertThat(seenByA).contains(updated.orElseThrow());
    assertThat(seenByA.orElseThrow().statusMessage()).isEqualTo("halfway");
  }
}
