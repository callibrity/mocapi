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

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.session.McpSessionStore;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers a {@link HazelcastMcpSessionStore} when Hazelcast is available and no other store is
 * defined.
 */
@AutoConfiguration(
    before = MocapiAutoConfiguration.class,
    afterName = "org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration")
@ConditionalOnClass(HazelcastInstance.class)
@ConditionalOnBean(HazelcastInstance.class)
public class HazelcastMcpSessionStoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(McpSessionStore.class)
  public McpSessionStore hazelcastMcpSessionStore(
      HazelcastInstance hazelcastInstance,
      @Value("${mocapi.session.hazelcast.map-name:mocapi-sessions}") String mapName) {
    return new HazelcastMcpSessionStore(hazelcastInstance, mapName);
  }
}
