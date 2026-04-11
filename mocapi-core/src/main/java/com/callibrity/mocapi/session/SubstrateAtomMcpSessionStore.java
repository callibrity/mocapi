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
package com.callibrity.mocapi.session;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jwcarman.substrate.atom.AtomExpiredException;
import org.jwcarman.substrate.atom.AtomFactory;

/**
 * {@link McpSessionStore} implementation backed by substrate's {@link AtomFactory} SPI.
 *
 * <p>Each session is stored as a typed {@code Atom<McpSession>} keyed by session ID. The backing
 * storage is whatever {@code substrate-<backend>} module is on the classpath — substrate's {@code
 * SubstrateAutoConfiguration} assembles an {@code AtomFactory} bean from the selected {@code
 * AtomSpi} plus a {@code CodecFactory} and {@code NotifierSpi}. This adapter simply delegates every
 * {@link McpSessionStore} operation to the factory.
 *
 * <p>If no backend {@code AtomSpi} is on the classpath, substrate falls back to its in-memory
 * implementation (and logs a WARN) — matching the behavior of the old {@code
 * InMemoryMcpSessionStore} fallback this class replaces.
 *
 * <p><b>TTL on update</b>: substrate's {@code Atom.set(value, ttl)} always assigns a fresh TTL —
 * there is no "keep TTL" primitive in the SPI. This adapter passes the configured session timeout
 * on every {@link #update(String, McpSession)} call, which effectively touches the session on each
 * write. That is a slight behavior change from the previous Redis-backed {@code SET ... KEEPTTL}
 * path, but it matches the intent that actively-updated sessions should stay alive.
 */
@RequiredArgsConstructor
public class SubstrateAtomMcpSessionStore implements McpSessionStore {

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
    } catch (AtomExpiredException e) {
      // Session expired between find and update — silently swallow, matching the previous
      // Redis SET XX / JDBC UPDATE WHERE id=? semantics. Callers that need to react to
      // missing sessions should use find() first.
    }
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    if (sessionId == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(atomFactory.connect(sessionId, McpSession.class).get().value());
    } catch (AtomExpiredException e) {
      return Optional.empty();
    }
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    atomFactory.connect(sessionId, McpSession.class).touch(ttl);
  }

  @Override
  public void delete(String sessionId) {
    atomFactory.connect(sessionId, McpSession.class).delete();
  }
}
