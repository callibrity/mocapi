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

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;

/**
 * Hazelcast {@link IMap}-backed {@link McpSessionStore}. Sessions are stored as {@link McpSession}
 * values with per-entry TTL. The {@link #update} method preserves the existing TTL by reading the
 * entry's expiration time and recomputing the remaining duration.
 */
@RequiredArgsConstructor
public class HazelcastMcpSessionStore implements McpSessionStore {

  private final HazelcastInstance hazelcastInstance;
  private final String mapName;

  private IMap<String, McpSession> map() {
    return hazelcastInstance.getMap(mapName);
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    map().set(session.sessionId(), session, ttl.toSeconds(), TimeUnit.SECONDS);
  }

  @Override
  public void update(String sessionId, McpSession session) {
    IMap<String, McpSession> map = map();
    EntryView<String, McpSession> view = map.getEntryView(sessionId);
    if (view == null) {
      return;
    }
    long remainingMillis = view.getExpirationTime() - System.currentTimeMillis();
    if (remainingMillis <= 0) {
      return;
    }
    map.set(sessionId, session, remainingMillis, TimeUnit.MILLISECONDS);
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    return Optional.ofNullable(map().get(sessionId));
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    map().setTtl(sessionId, ttl.toSeconds(), TimeUnit.SECONDS);
  }

  @Override
  public void delete(String sessionId) {
    map().delete(sessionId);
  }
}
