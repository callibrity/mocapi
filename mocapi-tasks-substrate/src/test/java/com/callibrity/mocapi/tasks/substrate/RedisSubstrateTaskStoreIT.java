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

import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.mocapi.tasks.store.TaskStoreContractTest;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
    CodecFactory codecFactory = new JacksonCodecFactory(JsonMapper.builder().build());
    AtomFactory atomFactory =
        new DefaultAtomFactory(
            new RedisAtomSpi(commands, "tck:" + UUID.randomUUID() + ":"),
            codecFactory,
            PayloadTransformer.IDENTITY,
            new DefaultNotifier(new InMemoryNotifier(), codecFactory),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    return new SubstrateTaskStore(atomFactory, clock, "mocapi:tasks:");
  }
}
