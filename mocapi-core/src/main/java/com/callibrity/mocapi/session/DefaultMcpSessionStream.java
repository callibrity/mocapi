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
package com.callibrity.mocapi.session;

import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;

/**
 * Package-private implementation that wraps an Odyssey stream identified by a stable name plus an
 * encrypting {@link SseEventMapper} that rewrites outbound event IDs with session-bound encryption.
 *
 * <p>The publisher is adopted eagerly at construction time so {@link #publishJson(Object)}, {@link
 * #subscribe()}, and {@link #resumeAfter(String)} can all share the same underlying journal without
 * any additional create/adopt dance.
 */
class DefaultMcpSessionStream implements McpSessionStream {

  private final Odyssey odyssey;
  private final OdysseyPublisher<JsonNode> publisher;
  private final SseEventMapper<JsonNode> mapper;

  DefaultMcpSessionStream(Odyssey odyssey, String name, SseEventMapper<JsonNode> mapper) {
    this.odyssey = odyssey;
    this.publisher = odyssey.publisher(name, JsonNode.class);
    this.mapper = mapper;
  }

  @Override
  public void publishJson(Object payload) {
    publisher.publish((JsonNode) payload);
  }

  @Override
  public void close() {
    publisher.complete();
  }

  @Override
  public SseEmitter subscribe() {
    return odyssey.subscribe(publisher.name(), JsonNode.class, cfg -> cfg.mapper(mapper));
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId) {
    return odyssey.resume(publisher.name(), JsonNode.class, lastEventId, cfg -> cfg.mapper(mapper));
  }
}
