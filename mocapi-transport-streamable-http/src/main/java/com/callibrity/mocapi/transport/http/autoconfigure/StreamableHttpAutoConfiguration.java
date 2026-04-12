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
package com.callibrity.mocapi.transport.http.autoconfigure;

import com.callibrity.mocapi.protocol.McpProtocol;
import com.callibrity.mocapi.protocol.autoconfigure.MocapiProtocolAutoConfiguration;
import com.callibrity.mocapi.protocol.autoconfigure.MocapiProtocolProperties;
import com.callibrity.mocapi.protocol.session.McpSessionService;
import com.callibrity.mocapi.transport.http.McpRequestValidator;
import com.callibrity.mocapi.transport.http.StreamableHttpController;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.jwcarman.odyssey.core.Odyssey;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = MocapiProtocolAutoConfiguration.class)
@ConditionalOnBean(McpProtocol.class)
@EnableConfigurationProperties(MocapiProtocolProperties.class)
@RequiredArgsConstructor
public class StreamableHttpAutoConfiguration {

  private final MocapiProtocolProperties props;

  @Bean
  @ConditionalOnMissingBean(McpRequestValidator.class)
  public McpRequestValidator mcpProtocolRequestValidator() {
    return new McpRequestValidator(props.getAllowedOrigins());
  }

  @Bean
  @ConditionalOnMissingBean(StreamableHttpController.class)
  public StreamableHttpController mcpProtocolStreamableHttpController(
      McpProtocol protocol,
      McpRequestValidator validator,
      McpSessionService sessionService,
      Odyssey odyssey,
      ObjectMapper objectMapper) {
    byte[] masterKey = Base64.getDecoder().decode(props.getSessionEncryptionMasterKey());
    return new StreamableHttpController(
        protocol, validator, sessionService, odyssey, objectMapper, masterKey);
  }
}
