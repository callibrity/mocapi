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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.ServerCapabilities;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class McpSessionServiceTest {

  private static final Duration TTL = Duration.ofMinutes(30);

  @Mock private McpSessionStore store;

  private McpSessionService service;

  @BeforeEach
  void setUp() {
    service =
        new McpSessionService(
            store,
            TTL,
            new Implementation("test-server", null, "1.0"),
            null,
            new ServerCapabilities(null, null, null, null, null));
  }

  @Test
  void createSavesToStoreAndReturnsSessionId() {
    McpSession session =
        new McpSession(
            "my-session-id",
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test", null, "1.0"));

    String sessionId = service.create(session);

    assertThat(sessionId).isEqualTo("my-session-id");
    ArgumentCaptor<McpSession> captor = ArgumentCaptor.forClass(McpSession.class);
    verify(store).save(captor.capture(), eq(TTL));
    McpSession saved = captor.getValue();
    assertThat(saved.sessionId()).isEqualTo("my-session-id");
    assertThat(saved.protocolVersion()).isEqualTo("2025-11-25");
  }

  @Test
  void findReturnsSession() {
    McpSession session =
        new McpSession("session-1", "2025-11-25", null, null, LoggingLevel.WARNING);
    when(store.find("session-1")).thenReturn(Optional.of(session));

    Optional<McpSession> result = service.find("session-1");

    assertThat(result).contains(session);
  }

  @Test
  void findReturnsEmptyWhenNotFound() {
    when(store.find("unknown")).thenReturn(Optional.empty());

    Optional<McpSession> result = service.find("unknown");

    assertThat(result).isEmpty();
  }

  @Test
  void deleteRemovesFromStore() {
    service.delete("session-1");

    verify(store).delete("session-1");
  }

  @Test
  void setLogLevelUpdatesSessionInStore() {
    McpSession session =
        new McpSession("session-1", "2025-11-25", null, null, LoggingLevel.WARNING);
    when(store.find("session-1")).thenReturn(Optional.of(session));

    service.setLogLevel("session-1", LoggingLevel.DEBUG);

    ArgumentCaptor<McpSession> captor = ArgumentCaptor.forClass(McpSession.class);
    verify(store).update(eq("session-1"), captor.capture());
    assertThat(captor.getValue().logLevel()).isEqualTo(LoggingLevel.DEBUG);
  }

  @Test
  void setLogLevelDoesNothingWhenSessionNotFound() {
    when(store.find("unknown")).thenReturn(Optional.empty());

    service.setLogLevel("unknown", LoggingLevel.DEBUG);

    verify(store, never()).update(anyString(), any());
  }
}
