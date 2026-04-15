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

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jwcarman.substrate.atom.AtomExpiredException;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.atom.AtomNotFoundException;

@RequiredArgsConstructor
public class AtomMcpSessionStore implements McpSessionStore {

  private final AtomFactory atomFactory;
  private final Duration sessionTimeout;

  @Override
  public void save(McpSession session, Duration ttl) {
    atomFactory.create(session.sessionId(), McpSession.class, session, ttl);
  }

  @Override
  public void update(String sessionId, McpSession session) {
    try {
      atomFactory.connect(sessionId, McpSession.class).set(session, sessionTimeout);
    } catch (AtomExpiredException | AtomNotFoundException _) {
      // Session expired or evicted between find and update — silently swallow.
    }
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(atomFactory.connect(sessionId, McpSession.class).get().value());
    } catch (AtomExpiredException | AtomNotFoundException _) {
      return Optional.empty();
    }
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    try {
      atomFactory.connect(sessionId, McpSession.class).touch(ttl);
    } catch (AtomExpiredException | AtomNotFoundException _) {
      // Session gone — touch is a best-effort no-op.
    }
  }

  @Override
  public void delete(String sessionId) {
    try {
      atomFactory.connect(sessionId, McpSession.class).delete();
    } catch (AtomExpiredException | AtomNotFoundException _) {
      // Session gone — delete is a best-effort no-op.
    }
  }
}
