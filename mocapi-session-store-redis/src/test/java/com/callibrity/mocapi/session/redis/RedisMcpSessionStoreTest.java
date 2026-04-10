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
package com.callibrity.mocapi.session.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.session.McpSession;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class RedisMcpSessionStoreTest {

  private static final String KEY_PREFIX = "mocapi:session:";

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private RedisMcpSessionStore store;
  private StringRedisTemplate redisTemplate;

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeEach
  void setUp() {
    LettuceConnectionFactory factory =
        new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    factory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(factory);
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    store = new RedisMcpSessionStore(redisTemplate, JsonMapper.builder().build(), KEY_PREFIX);
  }

  @Test
  void saveThenFindReturnsSameSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent().hasValue(session);
  }

  @Test
  void saveWithShortTtlExpiresSession() throws InterruptedException {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(1));
    Thread.sleep(2_000);
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void touchExtendsTtl() throws InterruptedException {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(1));
    store.touch(session.sessionId(), Duration.ofSeconds(10));
    Thread.sleep(2_000);
    assertThat(store.find(session.sessionId())).isPresent();
  }

  @Test
  void updatePreservesTtl() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofSeconds(10));

    McpSession updated = session.withLogLevel(com.callibrity.mocapi.model.LoggingLevel.DEBUG);
    store.update(session.sessionId(), updated);

    Long ttl = redisTemplate.getExpire(KEY_PREFIX + session.sessionId());
    assertThat(ttl).isGreaterThan(5);
    assertThat(store.find(session.sessionId())).isPresent().hasValue(updated);
  }

  @Test
  void deleteRemovesKey() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    store.delete(session.sessionId());
    assertThat(store.find(session.sessionId())).isEmpty();
  }

  @Test
  void findOnNonExistentKeyReturnsEmpty() {
    assertThat(store.find("nonexistent")).isEmpty();
  }

  @Test
  void deleteOnNonExistentKeyDoesNotFail() {
    store.delete("nonexistent");
  }
}
