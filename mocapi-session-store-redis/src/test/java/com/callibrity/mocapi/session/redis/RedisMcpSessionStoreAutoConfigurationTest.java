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
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.session.McpSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RedisMcpSessionStoreAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RedisMcpSessionStoreAutoConfiguration.class));

  @Test
  void registersRedisStoreWhenRedisIsAvailable() {
    runner
        .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
        .withBean(
            StringRedisTemplate.class,
            () -> new StringRedisTemplate(mock(RedisConnectionFactory.class)))
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(RedisMcpSessionStore.class);
            });
  }

  @Test
  void userProvidedBeanTakesPrecedence() {
    McpSessionStore userStore = mock(McpSessionStore.class);
    runner
        .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
        .withBean(
            StringRedisTemplate.class,
            () -> new StringRedisTemplate(mock(RedisConnectionFactory.class)))
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .withBean(McpSessionStore.class, () -> userStore)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class)).isSameAs(userStore);
            });
  }

  @Test
  void doesNotRegisterWithoutRedisConnectionFactory() {
    runner
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .run(context -> assertThat(context).doesNotHaveBean(McpSessionStore.class));
  }
}
