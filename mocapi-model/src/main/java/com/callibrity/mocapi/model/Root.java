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
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.node.ObjectNode;

/**
 * A root directory or file the server can operate on; the {@code uri} must start with {@code
 * file://}. Element of {@link ListRootsResult}.
 *
 * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577); remains in the specification
 *     for at least twelve months. The spec's suggested migration is for servers to elicit needed
 *     paths from the user instead of enumerating client roots.
 */
@Deprecated(since = "2026-07-28")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Root(String uri, String name, @JsonProperty("_meta") ObjectNode meta) {}
