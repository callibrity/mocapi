package com.callibrity.mocapi.server.substrate;

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
import org.jwcarman.substrate.journal.JournalFactory;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

public class SubstrateTestSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ShutdownCoordinator SHUTDOWN_COORDINATOR = new ShutdownCoordinator();
    public static final JacksonCodecFactory CODEC_FACTORY = new JacksonCodecFactory(MAPPER);
    private static final Notifier NOTIFIER = new DefaultNotifier(new InMemoryNotifier(), CODEC_FACTORY);
    private static final AtomFactory ATOM_FACTORY = new DefaultAtomFactory(new InMemoryAtomSpi(), CODEC_FACTORY, NOTIFIER, Duration.ofMinutes(5), SHUTDOWN_COORDINATOR);
    private static final JournalFactory JOURNAL_FACTORY = new DefaultJournalFactory(new InMemoryJournalSpi(), CODEC_FACTORY, NOTIFIER, new JournalLimits(1024, Duration.ofMinutes(5), Duration.ofMinutes(5), Duration.ofMinutes(5)), SHUTDOWN_COORDINATOR);
    private static final MailboxFactory MAILBOX_FACTORY = new DefaultMailboxFactory(new InMemoryMailboxSpi(), CODEC_FACTORY, NOTIFIER, Duration.ofMinutes(5), SHUTDOWN_COORDINATOR);

    private SubstrateTestSupport() {

    }

    public static AtomFactory atomFactory() {
        return ATOM_FACTORY;
    }

    public static JournalFactory journalFactory() {
        return JOURNAL_FACTORY;
    }

    public static MailboxFactory mailboxFactory() {
        return MAILBOX_FACTORY;
    }
}
