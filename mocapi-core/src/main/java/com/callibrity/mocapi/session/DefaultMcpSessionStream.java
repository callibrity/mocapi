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

import java.util.Map;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Package-private implementation that wraps an {@link OdysseyStream} and encrypting mapper. */
class DefaultMcpSessionStream implements McpSessionStream {

  private final OdysseyStream stream;
  private final SseEventMapper mapper;

  DefaultMcpSessionStream(OdysseyStream stream, SseEventMapper mapper) {
    this.stream = stream;
    this.mapper = mapper;
  }

  @Override
  public void publishJson(Object payload) {
    stream.publishJson(payload);
  }

  @Override
  public void close() {
    stream.close();
  }

  @Override
  public SseEmitter subscribe() {
    stream.publishJson(Map.of());
    return stream.subscriber().mapper(mapper).subscribe();
  }

  @Override
  public SseEmitter resumeAfter(String lastEventId) {
    return stream.subscriber().mapper(mapper).resumeAfter(lastEventId);
  }
}
