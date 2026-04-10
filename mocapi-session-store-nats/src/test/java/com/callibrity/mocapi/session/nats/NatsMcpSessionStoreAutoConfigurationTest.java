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
package com.callibrity.mocapi.session.nats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.MocapiProperties;
import com.callibrity.mocapi.session.McpSessionStore;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class NatsMcpSessionStoreAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(NatsMcpSessionStoreAutoConfiguration.class));

  @Test
  void registersNatsStoreWhenConnectionIsAvailable() throws Exception {
    Connection mockConnection = mock(Connection.class);
    KeyValueManagement mockKvm = mock(KeyValueManagement.class);
    when(mockConnection.keyValueManagement()).thenReturn(mockKvm);
    when(mockKvm.getBucketInfo(anyString())).thenThrow(mock(JetStreamApiException.class));
    when(mockKvm.create(any())).thenReturn(mock(KeyValueStatus.class));
    when(mockConnection.keyValue(anyString())).thenReturn(mock(KeyValue.class));

    runner
        .withBean(Connection.class, () -> mockConnection)
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .withBean(MocapiProperties.class, MocapiProperties::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class))
                  .isInstanceOf(NatsMcpSessionStore.class);
            });
  }

  @Test
  void doesNotRegisterWhenConnectionClassIsAbsent() {
    runner
        .withClassLoader(new FilteredClassLoader(Connection.class))
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .run(context -> assertThat(context).doesNotHaveBean(McpSessionStore.class));
  }

  @Test
  void doesNotRegisterWithoutConnectionBean() {
    runner
        .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
        .run(context -> assertThat(context).doesNotHaveBean(McpSessionStore.class));
  }
}
