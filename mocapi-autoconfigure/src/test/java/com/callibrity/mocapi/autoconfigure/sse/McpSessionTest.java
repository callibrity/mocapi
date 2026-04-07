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
package com.callibrity.mocapi.autoconfigure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;

class McpSessionTest {

  private static McpSession createSession() {
    return new McpSession("test-session-id", mock(OdysseyStream.class));
  }

  @Test
  void sessionShouldHaveProvidedId() {
    OdysseyStream stream = mock(OdysseyStream.class);
    var session = new McpSession("my-session-id", stream);
    assertThat(session.getSessionId()).isEqualTo("my-session-id");
  }

  @Test
  void sessionShouldTrackCreatedAt() {
    var session = createSession();
    assertThat(session.getCreatedAt()).isNotNull();
  }

  @Test
  void sessionShouldHoldNotificationStream() {
    OdysseyStream stream = mock(OdysseyStream.class);
    var session = new McpSession("id", stream);
    assertThat(session.getNotificationStream()).isSameAs(stream);
  }

  @Test
  void isInactiveShouldReturnFalseForActiveSession() {
    var session = createSession();
    assertThat(session.isInactive(3600)).isFalse();
  }

  @Test
  void isInactiveShouldReturnTrueForExpiredSession() {
    var session = createSession();
    assertThat(session.isInactive(0)).isTrue();
  }

  @Test
  void updateActivityShouldRefreshLastActivity() {
    var session = createSession();
    var before = session.getLastActivity();
    session.updateActivity();
    assertThat(session.getLastActivity()).isAfterOrEqualTo(before);
  }
}
