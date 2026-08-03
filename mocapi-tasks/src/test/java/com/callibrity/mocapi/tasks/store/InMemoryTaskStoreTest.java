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
package com.callibrity.mocapi.tasks.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.callibrity.mocapi.tasks.model.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InMemoryTaskStoreTest extends TaskStoreContractTest {

  @Override
  protected TaskStore newStore(Clock clock) {
    return new InMemoryTaskStore(clock, Duration.ofDays(1));
  }

  @Test
  void sweeper_physically_removes_expired_records_without_any_get() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    try (InMemoryTaskStore store = new InMemoryTaskStore(clock, Duration.ofMillis(20))) {
      store.create(
          new TaskRecord(
              "t1",
              "demo.tool",
              null,
              "user-1",
              "2026-07-28",
              null,
              TaskStatus.WORKING,
              "0",
              clock.instant(),
              clock.instant(),
              Duration.ofMillis(50),
              Duration.ofSeconds(1),
              List.of(),
              Map.of(),
              null,
              null,
              0L));
      assertThat(store.size()).isEqualTo(1);

      clock.advance(Duration.ofMinutes(5));

      await()
          .atMost(Duration.ofSeconds(2))
          .pollInterval(Duration.ofMillis(10))
          .until(() -> store.size() == 0);

      assertThat(store.size()).isZero();
    }
  }

  /** A {@link Clock} whose {@link #instant()} can be advanced manually. */
  private static final class MutableClock extends Clock {
    private volatile Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    static MutableClock at(Instant instant) {
      return new MutableClock(instant);
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
