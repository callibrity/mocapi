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
package com.callibrity.mocapi.session.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.RootsCapability;
import com.callibrity.mocapi.model.SamplingCapability;
import com.callibrity.mocapi.session.McpSession;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HazelcastMcpSessionStoreTest {

  private static final String MAP_NAME = "mocapi-sessions";

  private HazelcastInstance hazelcastInstance;
  private HazelcastMcpSessionStore store;

  private static Config isolatedConfig() {
    Config config = new Config();
    config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
    config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
    config.setClusterName("test-" + UUID.randomUUID());
    return config;
  }

  private static McpSession sessionWithId() {
    return new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"))
        .withSessionId(UUID.randomUUID().toString());
  }

  @BeforeEach
  void setUp() {
    hazelcastInstance = Hazelcast.newHazelcastInstance(isolatedConfig());
    store = new HazelcastMcpSessionStore(hazelcastInstance, MAP_NAME);
  }

  @AfterEach
  void tearDown() {
    hazelcastInstance.shutdown();
  }

  @Test
  void saveThenFindReturnsSameSession() {
    McpSession session = sessionWithId();
    store.save(session, Duration.ofHours(1));
    assertThat(store.find(session.sessionId())).isPresent().hasValue(session);
  }

  @Test
  void roundTripsAllFields() {
    McpSession session =
        new McpSession(
                "2025-11-25",
                new ClientCapabilities(
                    new RootsCapability(true),
                    new SamplingCapability(),
                    new ElicitationCapability()),
                new Implementation("test-client", "Test Client", "2.0"),
                LoggingLevel.DEBUG)
            .withSessionId(UUID.randomUUID().toString());

    store.save(session, Duration.ofHours(1));

    McpSession found = store.find(session.sessionId()).orElseThrow();
    assertThat(found.protocolVersion()).isEqualTo("2025-11-25");
    assertThat(found.capabilities().roots().listChanged()).isTrue();
    assertThat(found.capabilities().sampling()).isNotNull();
    assertThat(found.capabilities().elicitation()).isNotNull();
    assertThat(found.clientInfo().name()).isEqualTo("test-client");
    assertThat(found.clientInfo().title()).isEqualTo("Test Client");
    assertThat(found.clientInfo().version()).isEqualTo("2.0");
    assertThat(found.logLevel()).isEqualTo(LoggingLevel.DEBUG);
    assertThat(found.sessionId()).isEqualTo(session.sessionId());
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

    McpSession updated = session.withLogLevel(LoggingLevel.DEBUG);
    store.update(session.sessionId(), updated);

    assertThat(store.find(session.sessionId())).isPresent().hasValue(updated);

    long remainingTtl =
        hazelcastInstance.getMap(MAP_NAME).getEntryView(session.sessionId()).getExpirationTime()
            - System.currentTimeMillis();
    assertThat(remainingTtl).isGreaterThan(5_000);
  }

  @Test
  void deleteRemovesEntry() {
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
