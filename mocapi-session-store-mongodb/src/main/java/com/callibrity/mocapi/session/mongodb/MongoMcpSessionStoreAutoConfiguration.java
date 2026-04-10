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
package com.callibrity.mocapi.session.mongodb;

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.session.McpSessionStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers a {@link MongoMcpSessionStore} when a {@link MongoTemplate} is available. The in-memory
 * fallback in {@link MocapiAutoConfiguration} steps aside via its own
 * {@code @ConditionalOnMissingBean} once this backend bean is present.
 */
@AutoConfiguration(before = MocapiAutoConfiguration.class, after = DataMongoAutoConfiguration.class)
@ConditionalOnClass(MongoTemplate.class)
@ConditionalOnBean(MongoTemplate.class)
public class MongoMcpSessionStoreAutoConfiguration {

  @Bean
  public McpSessionStore mongoMcpSessionStore(
      MongoTemplate mongoTemplate,
      ObjectMapper objectMapper,
      @Value("${mocapi.session.mongodb.collection:mocapi_sessions}") String collectionName) {
    return new MongoMcpSessionStore(mongoTemplate, objectMapper, collectionName);
  }
}
