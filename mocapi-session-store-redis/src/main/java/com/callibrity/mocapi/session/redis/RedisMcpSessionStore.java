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

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed {@link McpSessionStore}. Each session is stored as a JSON string under key {@code
 * <prefix><sessionId>} with a Redis-native TTL. The {@link #update} method preserves the existing
 * TTL via {@code SET ... KEEPTTL} (requires Redis 6.0+).
 */
@RequiredArgsConstructor
public class RedisMcpSessionStore implements McpSessionStore {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final String keyPrefix;

  @Override
  public void save(McpSession session, Duration ttl) {
    String key = keyPrefix + session.sessionId();
    String json = objectMapper.writeValueAsString(session);
    redisTemplate.opsForValue().set(key, json, ttl);
  }

  @Override
  public void update(String sessionId, McpSession session) {
    String key = keyPrefix + sessionId;
    String json = objectMapper.writeValueAsString(session);
    redisTemplate.execute(
        (RedisCallback<Boolean>)
            connection ->
                connection
                    .stringCommands()
                    .set(
                        redisTemplate.getStringSerializer().serialize(key),
                        redisTemplate.getStringSerializer().serialize(json),
                        Expiration.keepTtl(),
                        SetOption.ifPresent()));
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    String key = keyPrefix + sessionId;
    String json = redisTemplate.opsForValue().get(key);
    if (json == null) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(json, McpSession.class));
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    String key = keyPrefix + sessionId;
    redisTemplate.expire(key, ttl);
  }

  @Override
  public void delete(String sessionId) {
    String key = keyPrefix + sessionId;
    redisTemplate.delete(key);
  }
}
