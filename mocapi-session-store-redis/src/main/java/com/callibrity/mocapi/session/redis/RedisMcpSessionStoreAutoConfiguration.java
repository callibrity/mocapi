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

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.session.McpSessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers a {@link RedisMcpSessionStore} when Spring Data Redis is on the classpath and a {@link
 * RedisConnectionFactory} bean is available. The in-memory fallback in {@link
 * MocapiAutoConfiguration} steps aside via its own {@code @ConditionalOnMissingBean} once this
 * backend bean is present.
 */
@AutoConfiguration(before = MocapiAutoConfiguration.class, after = DataRedisAutoConfiguration.class)
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnBean(RedisConnectionFactory.class)
public class RedisMcpSessionStoreAutoConfiguration {

  @Bean
  public McpSessionStore redisMcpSessionStore(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${mocapi.session.redis.key-prefix:mocapi:session:}") String keyPrefix) {
    return new RedisMcpSessionStore(redisTemplate, objectMapper, keyPrefix);
  }
}
