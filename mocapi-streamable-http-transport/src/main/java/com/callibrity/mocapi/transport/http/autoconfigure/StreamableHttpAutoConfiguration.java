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

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerProperties;
import com.callibrity.mocapi.transport.http.McpRequestValidator;
import com.callibrity.mocapi.transport.http.StreamableHttpController;
import com.callibrity.mocapi.transport.http.sse.Ciphers;
import com.callibrity.mocapi.transport.http.sse.DefaultSseStreamFactory;
import com.callibrity.mocapi.transport.http.sse.SseStreamFactory;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.jwcarman.odyssey.core.Odyssey;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = MocapiServerAutoConfiguration.class)
@ConditionalOnBean(McpServer.class)
@EnableConfigurationProperties(MocapiServerProperties.class)
@RequiredArgsConstructor
public class StreamableHttpAutoConfiguration {

  private final MocapiServerProperties props;

  @Bean
  @ConditionalOnMissingBean(McpRequestValidator.class)
  public McpRequestValidator mcpProtocolRequestValidator() {
    return new McpRequestValidator(props.allowedOrigins());
  }

  @Bean
  @ConditionalOnMissingBean(SseStreamFactory.class)
  public DefaultSseStreamFactory mcpProtocolSseStreamFactory(
      Odyssey odyssey, ObjectMapper objectMapper) {
    byte[] masterKey = decodeMasterKey(props.sessionEncryptionMasterKey());
    Ciphers.validateAesGcmKey(masterKey);
    return new DefaultSseStreamFactory(odyssey, objectMapper, masterKey);
  }

  @Bean
  @ConditionalOnMissingBean
  public ContextSnapshotFactory mcpContextSnapshotFactory() {
    return ContextSnapshotFactory.builder().build();
  }

  @Bean
  @ConditionalOnMissingBean(StreamableHttpController.class)
  public StreamableHttpController mcpProtocolStreamableHttpController(
      McpServer protocol,
      McpRequestValidator validator,
      SseStreamFactory sseStreamFactory,
      ObjectMapper objectMapper,
      ContextSnapshotFactory contextSnapshotFactory) {
    return new StreamableHttpController(
        protocol, validator, sseStreamFactory, objectMapper, contextSnapshotFactory);
  }

  private static byte[] decodeMasterKey(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      throw new IllegalStateException(
          """
          mocapi.session-encryption-master-key is required but not set. Provide a \
          base64-encoded 32-byte (AES-256) key via application.properties, \
          application.yml, or the MOCAPI_SESSION_ENCRYPTION_MASTER_KEY environment \
          variable. Generate one with: openssl rand -base64 32""");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "mocapi.session-encryption-master-key is not valid base64. Generate a new one "
              + "with: openssl rand -base64 32",
          e);
    }
    if (decoded.length != 32) {
      throw new IllegalStateException(
          "mocapi.session-encryption-master-key decodes to "
              + decoded.length
              + " bytes; AES-256 requires exactly 32. Generate a new one with: "
              + "openssl rand -base64 32");
    }
    return decoded;
  }
}
