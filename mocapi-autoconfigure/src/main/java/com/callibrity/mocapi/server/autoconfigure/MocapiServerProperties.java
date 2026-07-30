/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.autoconfigure;

import com.callibrity.mocapi.model.CacheScope;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "mocapi")
public record MocapiServerProperties(
    String serverName,
    String serverTitle,
    String serverVersion,
    String instructions,
    List<String> allowedOrigins,
    Duration streamTimeout,
    @DefaultValue("true") boolean emitServerInfo,
    Mrtr mrtr,
    Cache cache,
    Pagination pagination) {

  /**
   * Backstop async timeout for a per-request SSE response stream ({@code mocapi.stream-timeout}).
   * Bounds a hung handler that never sends its final response; {@code null} falls back to {@link
   * #DEFAULT_STREAM_TIMEOUT}.
   */
  public static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(5);

  public Duration streamTimeoutOrDefault() {
    return streamTimeout != null ? streamTimeout : DEFAULT_STREAM_TIMEOUT;
  }

  /**
   * MRTR elicitation replay (ADR-0021). An empty {@code secret} means an ephemeral key is generated
   * at startup — requestState tokens then die with the process (dev only).
   */
  public record Mrtr(String secret, Duration ttl) {}

  /**
   * Cache directives stamped onto the six cacheable results. {@code listTtl} covers the four list
   * results and {@code server/discover}; {@code readTtl} covers {@code resources/read}.
   */
  public record Cache(Duration listTtl, Duration readTtl, CacheScope scope) {}

  public record Pagination(int pageSize) {}
}
