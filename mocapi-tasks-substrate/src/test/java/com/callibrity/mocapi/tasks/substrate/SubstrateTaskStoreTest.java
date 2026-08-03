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
import java.time.Clock;
import java.time.Duration;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.atom.InMemoryAtomSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the {@link TaskStoreContractTest} TCK against {@link SubstrateTaskStore} on Substrate's
 * in-memory Atom SPI. Every write still round-trips through {@code codec-jackson} bytes, so
 * serialization is genuinely exercised.
 */
class SubstrateTaskStoreTest extends TaskStoreContractTest {

  @Override
  protected TaskStore newStore(Clock clock) {
    CodecFactory codecFactory = new JacksonCodecFactory(JsonMapper.builder().build());
    AtomFactory atomFactory =
        new DefaultAtomFactory(
            new InMemoryAtomSpi(),
            codecFactory,
            PayloadTransformer.IDENTITY,
            new DefaultNotifier(new InMemoryNotifier(), codecFactory),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    return new SubstrateTaskStore(atomFactory, clock, "mocapi:tasks:");
  }
}
