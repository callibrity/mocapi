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
package com.callibrity.mocapi.server.cache;

import com.callibrity.mocapi.model.CacheScope;
import java.time.Duration;

/**
 * Server-wide cache directives stamped onto the six cacheable results the spec defines ({@code
 * tools/list}, {@code prompts/list}, {@code resources/list}, {@code resources/templates/list},
 * {@code resources/read}, {@code server/discover}). The spec requires {@code ttlMs} and {@code
 * cacheScope} on every cacheable result; these settings are the configured values, sourced from the
 * {@code mocapi.cache.*} properties:
 *
 * <ul>
 *   <li>{@code mocapi.cache.list-ttl} — TTL for list-shaped results (the four {@code *List} results
 *       and {@code DiscoverResult}, whose payload is startup-static discovery metadata like the
 *       lists). Default {@code PT0S}.
 *   <li>{@code mocapi.cache.read-ttl} — TTL for {@code resources/read} results. Default {@code
 *       PT0S}.
 *   <li>{@code mocapi.cache.scope} — {@code public} or {@code private}. Default {@code private}.
 * </ul>
 *
 * <p>The defaults ({@code ttlMs=0}, {@code private}) tell clients not to cache — the conservative
 * stance a server should take until an operator opts in.
 *
 * @param listTtl TTL for list-shaped results (never {@code null}; {@code null} input becomes {@link
 *     Duration#ZERO})
 * @param readTtl TTL for read results (never {@code null}; {@code null} input becomes {@link
 *     Duration#ZERO})
 * @param scope cache scope for every cacheable result (never {@code null}; {@code null} input
 *     becomes {@link CacheScope#PRIVATE})
 */
public record CacheSettings(Duration listTtl, Duration readTtl, CacheScope scope) {

  private static final CacheSettings DEFAULTS =
      new CacheSettings(Duration.ZERO, Duration.ZERO, CacheScope.PRIVATE);

  public CacheSettings {
    listTtl = listTtl == null ? Duration.ZERO : listTtl;
    readTtl = readTtl == null ? Duration.ZERO : readTtl;
    scope = scope == null ? CacheScope.PRIVATE : scope;
    if (listTtl.isNegative() || readTtl.isNegative()) {
      throw new IllegalArgumentException("Cache TTLs must not be negative (ttlMs minimum is 0)");
    }
  }

  /** The conservative defaults: {@code ttlMs=0} everywhere, {@code private} scope. */
  public static CacheSettings defaults() {
    return DEFAULTS;
  }

  /** The configured list TTL in whole milliseconds, as the spec's {@code ttlMs} integer. */
  public long listTtlMs() {
    return listTtl.toMillis();
  }

  /** The configured read TTL in whole milliseconds, as the spec's {@code ttlMs} integer. */
  public long readTtlMs() {
    return readTtl.toMillis();
  }
}
