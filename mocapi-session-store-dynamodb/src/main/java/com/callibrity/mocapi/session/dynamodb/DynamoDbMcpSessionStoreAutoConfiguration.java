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
package com.callibrity.mocapi.session.dynamodb;

import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.session.McpSessionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers a {@link DynamoDbMcpSessionStore} when a {@link DynamoDbClient} is available. The
 * in-memory fallback in {@link MocapiAutoConfiguration} steps aside via its own
 * {@code @ConditionalOnMissingBean} once this backend bean is present.
 */
@AutoConfiguration(before = MocapiAutoConfiguration.class)
@ConditionalOnClass(DynamoDbClient.class)
@ConditionalOnBean(DynamoDbClient.class)
@ConditionalOnMissingBean(McpSessionStore.class)
@EnableConfigurationProperties(DynamoDbSessionStoreProperties.class)
public class DynamoDbMcpSessionStoreAutoConfiguration {

  @Bean
  McpSessionStore dynamoDbMcpSessionStore(
      DynamoDbClient dynamoDbClient,
      ObjectMapper objectMapper,
      DynamoDbSessionStoreProperties properties) {
    return new DynamoDbMcpSessionStore(dynamoDbClient, objectMapper, properties);
  }
}
