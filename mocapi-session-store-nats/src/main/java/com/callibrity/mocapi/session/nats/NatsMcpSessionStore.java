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

import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.KeyValueManagement;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.KeyValueEntry;
import io.nats.client.api.KeyValueOperation;
import io.nats.client.api.KeyValueStatus;
import io.nats.client.api.StorageType;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * NATS JetStream KV backed {@link McpSessionStore}. Each session is stored as a JSON-serialized
 * byte array in a single KV bucket. Expiration is handled by the bucket-level {@code max_age} (set
 * to {@code mocapi.session-timeout} at bucket-creation time); all sessions share the same TTL. The
 * per-call {@code ttl} parameter on {@link #save} is ignored — the bucket TTL governs.
 *
 * <p>Unlike the Redis implementation, {@link #update} resets the bucket-level age (NATS KV has no
 * KEEPTTL equivalent). In practice this is negligible because mocapi's request lifecycle calls
 * {@link #touch} shortly after any update.
 */
@Slf4j
public class NatsMcpSessionStore implements McpSessionStore {

  private final KeyValue kv;
  private final ObjectMapper objectMapper;

  public NatsMcpSessionStore(
      Connection connection, ObjectMapper objectMapper, String bucketName, Duration sessionTimeout)
      throws IOException, JetStreamApiException {
    this.objectMapper = objectMapper;
    KeyValueManagement kvm = connection.keyValueManagement();
    try {
      KeyValueStatus status = kvm.getBucketInfo(bucketName);
      Duration existingTtl = status.getTtl();
      if (!existingTtl.equals(sessionTimeout)) {
        log.warn(
            "NATS KV bucket '{}' has max_age {} but mocapi.session-timeout is {}. "
                + "Delete and recreate the bucket to apply the new timeout.",
            bucketName,
            existingTtl,
            sessionTimeout);
      }
    } catch (JetStreamApiException e) {
      kvm.create(
          KeyValueConfiguration.builder()
              .name(bucketName)
              .maxHistoryPerKey(1)
              .ttl(sessionTimeout)
              .storageType(StorageType.File)
              .build());
    }
    this.kv = connection.keyValue(bucketName);
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    byte[] payload = objectMapper.writeValueAsBytes(session);
    try {
      kv.put(session.sessionId(), payload);
    } catch (JetStreamApiException | IOException e) {
      throw new IllegalStateException("Failed to save session " + session.sessionId(), e);
    }
  }

  @Override
  public void update(String sessionId, McpSession session) {
    byte[] payload = objectMapper.writeValueAsBytes(session);
    try {
      kv.put(sessionId, payload);
    } catch (JetStreamApiException | IOException e) {
      throw new IllegalStateException("Failed to update session " + sessionId, e);
    }
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    try {
      KeyValueEntry entry = kv.get(sessionId);
      if (entry == null || entry.getOperation() != KeyValueOperation.PUT) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(entry.getValue(), McpSession.class));
    } catch (JetStreamApiException | IOException e) {
      throw new IllegalStateException("Failed to find session " + sessionId, e);
    }
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    try {
      KeyValueEntry entry = kv.get(sessionId);
      if (entry == null || entry.getOperation() != KeyValueOperation.PUT) {
        return;
      }
      kv.put(sessionId, entry.getValue());
    } catch (JetStreamApiException | IOException e) {
      throw new IllegalStateException("Failed to touch session " + sessionId, e);
    }
  }

  @Override
  public void delete(String sessionId) {
    try {
      kv.delete(sessionId);
    } catch (JetStreamApiException | IOException e) {
      throw new IllegalStateException("Failed to delete session " + sessionId, e);
    }
  }
}
