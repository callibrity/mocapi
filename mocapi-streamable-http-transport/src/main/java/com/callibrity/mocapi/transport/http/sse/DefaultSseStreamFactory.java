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
package com.callibrity.mocapi.transport.http.sse;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class DefaultSseStreamFactory implements SseStreamFactory {

  // ------------------------------ FIELDS ------------------------------

  private final Odyssey odyssey;
  private final ObjectMapper objectMapper;
  private final byte[] masterKey;

  // -------------------------- OTHER METHODS --------------------------

  @Override
  public SseStream responseStream(McpContext context) {
    var streamName = UUID.randomUUID().toString();
    var stream = odyssey.stream(streamName, JsonRpcMessage.class);
    return DefaultSseStream.create(stream, encryptingMapper(context.sessionId()));
  }

  @Override
  public SseStream sessionStream(McpContext context) {
    var stream = odyssey.stream(context.sessionId(), JsonRpcMessage.class);
    return DefaultSseStream.create(stream, encryptingMapper(context.sessionId()));
  }

  @Override
  public SseStream resumeStream(McpContext context, String lastEventId) {
    var plaintext = decrypt(context.sessionId(), lastEventId);
    int colonIndex = plaintext.lastIndexOf(':');
    if (colonIndex < 0) {
      throw new IllegalArgumentException("Invalid encrypted event ID format");
    }
    var streamName = plaintext.substring(0, colonIndex);
    var rawEventId = plaintext.substring(colonIndex + 1);
    var stream = odyssey.stream(streamName, JsonRpcMessage.class);
    return DefaultSseStream.create(stream, rawEventId, encryptingMapper(context.sessionId()));
  }

  private SseEventMapper<JsonRpcMessage> encryptingMapper(String sessionId) {
    return event -> {
      String plaintext = event.streamName() + ":" + event.id();
      String encryptedId = encrypt(sessionId, plaintext);
      SseEmitter.SseEventBuilder builder =
          SseEmitter.event().id(encryptedId).data(objectMapper.writeValueAsString(event.data()));
      if (event.eventType() != null) {
        builder.name(event.eventType());
      }
      return builder;
    };
  }

  private String encrypt(String sessionId, String plaintext) {
    try {
      byte[] encrypted =
          Ciphers.encryptAesGcm(masterKey, sessionId, plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  private String decrypt(String sessionId, String ciphertext) {
    byte[] combined;
    try {
      combined = Base64.getDecoder().decode(ciphertext);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Malformed Base64 in encrypted token", e);
    }
    try {
      byte[] plaintext = Ciphers.decryptAesGcm(masterKey, sessionId, combined);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("Invalid or tampered encrypted token", e);
    }
  }
}
