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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.session.McpSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveDescription;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DynamoDbMcpSessionStoreAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(DynamoDbMcpSessionStoreAutoConfiguration.class));

  private static DynamoDbClient mockDynamoDbClient() {
    DynamoDbClient client = mock(DynamoDbClient.class);
    when(client.describeTable(
            any(software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest.class)))
        .thenReturn(
            DescribeTableResponse.builder()
                .table(TableDescription.builder().tableName("mocapi_sessions").build())
                .build());
    when(client.describeTimeToLive(any(DescribeTimeToLiveRequest.class)))
        .thenReturn(
            DescribeTimeToLiveResponse.builder()
                .timeToLiveDescription(
                    TimeToLiveDescription.builder()
                        .timeToLiveStatus(TimeToLiveStatus.ENABLED)
                        .build())
                .build());
    return client;
  }

  @Test
  void registersDynamoDbStoreWhenClientIsAvailable() {
    runner
        .withBean(
            DynamoDbClient.class, DynamoDbMcpSessionStoreAutoConfigurationTest::mockDynamoDbClient)
        .withBean(ObjectMapper.class, JsonMapper::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(DynamoDbMcpSessionStore.class);
            });
  }

  @Test
  void userProvidedBeanTakesPrecedence() {
    McpSessionStore userStore = mock(McpSessionStore.class);
    runner
        .withBean(
            DynamoDbClient.class, DynamoDbMcpSessionStoreAutoConfigurationTest::mockDynamoDbClient)
        .withBean(ObjectMapper.class, JsonMapper::new)
        .withBean(McpSessionStore.class, () -> userStore)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class)).isSameAs(userStore);
            });
  }

  @Test
  void doesNotRegisterWithoutDynamoDbClient() {
    runner.run(context -> assertThat(context).doesNotHaveBean(McpSessionStore.class));
  }
}
