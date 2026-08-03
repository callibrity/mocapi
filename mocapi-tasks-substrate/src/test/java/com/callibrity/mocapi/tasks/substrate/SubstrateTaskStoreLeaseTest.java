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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.spi.TypeRef;
import org.jwcarman.substrate.BlockingSubscription;
import org.jwcarman.substrate.Subscriber;
import org.jwcarman.substrate.SubscriberConfig;
import org.jwcarman.substrate.Subscription;
import org.jwcarman.substrate.atom.Atom;
import org.jwcarman.substrate.atom.AtomAlreadyExistsException;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.atom.AtomNotFoundException;
import org.jwcarman.substrate.atom.Snapshot;

/**
 * Pins the remaining-TTL lease invariant: {@link SubstrateTaskStore} must pass {@code createdAt +
 * ttl − now} (not the full record TTL) to every backend write, using a hand-written capturing fake
 * of the Substrate {@link AtomFactory}/{@link Atom} SPI (the module uses no mocking framework).
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SubstrateTaskStoreLeaseTest {

  private static final String KEY_PREFIX = "mocapi:tasks:";

  private TaskRecord newRecord(String taskId, Instant createdAt, Duration ttl) {
    return new TaskRecord(
        taskId,
        "demo.tool",
        null,
        "user-1",
        "2026-07-28",
        null,
        TaskStatus.WORKING,
        "0",
        createdAt,
        createdAt,
        ttl,
        Duration.ofSeconds(1),
        List.of(),
        Map.of(),
        null,
        null,
        0L);
  }

  @Test
  void create_passes_remaining_time_to_deadline_not_full_ttl() {
    Instant now = Instant.parse("2026-08-02T00:00:00Z");
    Instant createdAt = now.minus(Duration.ofMinutes(15));
    Duration ttl = Duration.ofHours(1);
    CapturingAtomFactory factory = new CapturingAtomFactory();
    SubstrateTaskStore store =
        new SubstrateTaskStore(factory, Clock.fixed(now, ZoneId.of("UTC")), KEY_PREFIX);

    store.create(newRecord("t1", createdAt, ttl));

    assertThat(factory.createDurations).containsExactly(Duration.ofMinutes(45));
  }

  @Test
  void update_passes_shrinking_remaining_time_after_clock_advances() {
    Instant t0 = Instant.parse("2026-08-02T00:00:00Z");
    Duration ttl = Duration.ofHours(1);
    MutableClock clock = new MutableClock(t0);
    CapturingAtomFactory factory = new CapturingAtomFactory();
    SubstrateTaskStore store = new SubstrateTaskStore(factory, clock, KEY_PREFIX);
    store.create(newRecord("t1", t0, ttl));

    clock.advance(Duration.ofMinutes(20));
    store.update("t1", r -> r.working(clock.instant()));

    assertThat(factory.casDurations).containsExactly(Duration.ofMinutes(40));
  }

  @Test
  void keys_passed_to_the_factory_are_the_prefix_plus_task_id() {
    Instant now = Instant.parse("2026-08-02T00:00:00Z");
    CapturingAtomFactory factory = new CapturingAtomFactory();
    SubstrateTaskStore store =
        new SubstrateTaskStore(factory, Clock.fixed(now, ZoneId.of("UTC")), KEY_PREFIX);

    store.create(newRecord("abc", now, Duration.ofHours(1)));
    store.get("abc");

    assertThat(factory.createKeys).containsExactly("mocapi:tasks:abc");
    assertThat(factory.connectKeys).contains("mocapi:tasks:abc");
  }

  @Test
  void create_at_exact_deadline_still_stores_with_a_positive_clamped_lease() {
    Instant createdAt = Instant.parse("2026-08-02T00:00:00Z");
    Duration ttl = Duration.ofMinutes(30);
    Instant deadline = createdAt.plus(ttl);
    CapturingAtomFactory factory = new CapturingAtomFactory();
    SubstrateTaskStore store =
        new SubstrateTaskStore(factory, Clock.fixed(deadline, ZoneId.of("UTC")), KEY_PREFIX);
    TaskRecord rec = newRecord("t1", createdAt, ttl);

    store.create(rec);

    assertThat(factory.createDurations).hasSize(1);
    assertThat(factory.createDurations.get(0).isPositive()).isTrue();
    assertThat(store.get("t1")).contains(rec);
  }

  /** A {@link Clock} whose {@link #instant()} can be advanced manually between operations. */
  private static final class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  /** Backing storage for a single fake atom: current value (opaque) and CAS token. */
  private static final class StoredEntry {
    private final Object value;
    private final String token;

    StoredEntry(Object value, String token) {
      this.value = value;
      this.token = token;
    }
  }

  /**
   * Hand-written capturing fake of {@link AtomFactory}, recording every key and {@link Duration}
   * passed to {@code create} and every {@link Duration} passed to {@code compareAndSet}.
   */
  private static final class CapturingAtomFactory implements AtomFactory {
    private final Map<String, StoredEntry> storage = new ConcurrentHashMap<>();
    private final AtomicLong tokenSequence = new AtomicLong();
    final List<String> createKeys = new ArrayList<>();
    final List<String> connectKeys = new ArrayList<>();
    final List<Duration> createDurations = new ArrayList<>();
    final List<Duration> casDurations = new ArrayList<>();

    @Override
    public <T> Atom<T> create(String key, Class<T> type, T initial, Duration ttl) {
      createKeys.add(key);
      createDurations.add(ttl);
      StoredEntry existing =
          storage.putIfAbsent(
              key, new StoredEntry(initial, Long.toString(tokenSequence.incrementAndGet())));
      if (existing != null) {
        throw new AtomAlreadyExistsException(key);
      }
      return new CapturingAtom<>(key, type);
    }

    @Override
    public <T> Atom<T> create(String key, TypeRef<T> typeRef, T initial, Duration ttl) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> Atom<T> connect(String key, Class<T> type) {
      connectKeys.add(key);
      return new CapturingAtom<>(key, type);
    }

    @Override
    public <T> Atom<T> connect(String key, TypeRef<T> typeRef) {
      throw new UnsupportedOperationException();
    }

    /** An {@link Atom} handle bound to one storage key in the enclosing factory's map. */
    private final class CapturingAtom<T> implements Atom<T> {
      private final String key;
      private final Class<T> type;

      CapturingAtom(String key, Class<T> type) {
        this.key = key;
        this.type = type;
      }

      @Override
      public void set(T value, Duration ttl) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean compareAndSet(Snapshot<T> expected, T newValue, Duration ttl) {
        casDurations.add(ttl);
        StoredEntry current = storage.get(key);
        if (current == null || !current.token.equals(expected.token())) {
          return false;
        }
        storage.put(key, new StoredEntry(newValue, Long.toString(tokenSequence.incrementAndGet())));
        return true;
      }

      @Override
      public boolean touch(Duration ttl) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Snapshot<T> get() {
        StoredEntry current = storage.get(key);
        if (current == null) {
          throw new AtomNotFoundException(key);
        }
        return new Snapshot<>(type.cast(current.value), current.token);
      }

      @Override
      public void delete() {
        storage.remove(key);
      }

      @Override
      public BlockingSubscription<Snapshot<T>> subscribe() {
        throw new UnsupportedOperationException();
      }

      @Override
      public BlockingSubscription<Snapshot<T>> subscribe(Snapshot<T> since) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Subscription subscribe(Subscriber<Snapshot<T>> subscriber) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Subscription subscribe(Consumer<SubscriberConfig<Snapshot<T>>> configurer) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Subscription subscribe(Snapshot<T> since, Subscriber<Snapshot<T>> subscriber) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Subscription subscribe(
          Snapshot<T> since, Consumer<SubscriberConfig<Snapshot<T>>> configurer) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String key() {
        throw new UnsupportedOperationException();
      }
    }
  }
}
