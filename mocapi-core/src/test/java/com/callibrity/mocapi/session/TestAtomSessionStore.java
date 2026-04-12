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
package com.callibrity.mocapi.session;

import java.time.Duration;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.atom.InMemoryAtomSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import tools.jackson.databind.ObjectMapper;

/**
 * Test helper that builds a real {@link SubstrateAtomMcpSessionStore} backed by substrate's
 * in-memory {@code AtomSpi} and {@code NotifierSpi}. Exercises the same code path mocapi uses in
 * production when no backend module is on the classpath — no mocks, no special-case test doubles.
 */
public final class TestAtomSessionStore {

  private static final Duration DEFAULT_MAX_TTL = Duration.ofHours(1);

  private TestAtomSessionStore() {}

  public static SubstrateAtomMcpSessionStore create(ObjectMapper objectMapper) {
    return create(objectMapper, DEFAULT_MAX_TTL);
  }

  public static SubstrateAtomMcpSessionStore create(
      ObjectMapper objectMapper, Duration sessionTimeout) {
    var codecFactory = new JacksonCodecFactory(objectMapper);
    var notifier = new DefaultNotifier(new InMemoryNotifier(), codecFactory);
    var atomFactory =
        new DefaultAtomFactory(
            new InMemoryAtomSpi(), codecFactory, notifier, sessionTimeout,
            new ShutdownCoordinator());
    return new SubstrateAtomMcpSessionStore(atomFactory, sessionTimeout);
  }
}
