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
package com.callibrity.mocapi.server.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.callibrity.mocapi.server.substrate.SubstrateTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.substrate.atom.AtomFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AtomMcpSessionStoreTest {

  private final AtomFactory atomFactory = SubstrateTestSupport.atomFactory();
  private final Duration sessionTimeout = Duration.ofMinutes(30);
  private final AtomMcpSessionStore store = new AtomMcpSessionStore(atomFactory, sessionTimeout);

  private McpSession createSession(String sessionId) {
    return new McpSession(sessionId, "2025-03-26", null, null);
  }

  @Nested
  class Find {

    @Test
    void returns_empty_for_null_session_id() {
      assertThat(store.find(null)).isEmpty();
    }

    @Test
    void returns_empty_for_non_existent_session() {
      assertThat(store.find(UUID.randomUUID().toString())).isEmpty();
    }
  }

  @Nested
  class Missing_session_operations_are_no_ops {

    @Test
    void update_does_not_throw_for_expired_session() {
      String sessionId = UUID.randomUUID().toString();
      McpSession session = createSession(sessionId);
      // Update a session that was never saved — the atom connect will throw AtomExpiredException.
      // This should be silently swallowed.
      assertThatNoException().isThrownBy(() -> store.update(sessionId, session));
    }

    @Test
    void touch_does_not_throw_for_missing_session() {
      String sessionId = UUID.randomUUID().toString();
      assertThatNoException().isThrownBy(() -> store.touch(sessionId, sessionTimeout));
    }

    @Test
    void delete_does_not_throw_for_missing_session() {
      String sessionId = UUID.randomUUID().toString();
      assertThatNoException().isThrownBy(() -> store.delete(sessionId));
    }
  }
}
