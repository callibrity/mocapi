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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result of a {@code server/discover} request: the server's supported protocol versions,
 * capabilities, identity, and optional instructions. Extends the spec's {@code CacheableResult}, so
 * {@code ttlMs}, {@code cacheScope}, and {@code resultType} are required.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscoverResult(
    List<String> supportedVersions,
    ServerCapabilities capabilities,
    Implementation serverInfo,
    String instructions,
    long ttlMs,
    CacheScope cacheScope,
    String resultType) {}
