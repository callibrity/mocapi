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
package com.callibrity.mocapi.server.substrate;

import java.time.Duration;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.journal.DefaultJournalFactory;
import org.jwcarman.substrate.core.journal.JournalLimits;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.mailbox.DefaultMailboxFactory;
import org.jwcarman.substrate.core.memory.atom.InMemoryAtomSpi;
import org.jwcarman.substrate.core.memory.journal.InMemoryJournalSpi;
import org.jwcarman.substrate.core.memory.mailbox.InMemoryMailboxSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.notifier.Notifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import org.jwcarman.substrate.journal.JournalFactory;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * Creates fresh in-memory substrate factories for testing. Each method returns a new instance with
 * isolated state — no shared singletons, no cross-test contamination.
 */
public final class SubstrateTestSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JacksonCodecFactory CODEC_FACTORY = new JacksonCodecFactory(MAPPER);

  private SubstrateTestSupport() {}

  public static AtomFactory atomFactory() {
    return new DefaultAtomFactory(
        new InMemoryAtomSpi(),
        CODEC_FACTORY,
        PayloadTransformer.IDENTITY,
        notifier(),
        Duration.ofHours(1),
        new ShutdownCoordinator());
  }

  public static JournalFactory journalFactory() {
    return new DefaultJournalFactory(
        new InMemoryJournalSpi(),
        CODEC_FACTORY,
        PayloadTransformer.IDENTITY,
        notifier(),
        new JournalLimits(1024, Duration.ofHours(1), Duration.ofHours(1), Duration.ofHours(1)),
        new ShutdownCoordinator());
  }

  public static MailboxFactory mailboxFactory() {
    return new DefaultMailboxFactory(
        new InMemoryMailboxSpi(),
        CODEC_FACTORY,
        PayloadTransformer.IDENTITY,
        notifier(),
        Duration.ofHours(1),
        new ShutdownCoordinator());
  }

  private static Notifier notifier() {
    return new DefaultNotifier(new InMemoryNotifier(), CODEC_FACTORY);
  }
}
