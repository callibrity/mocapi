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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.node.ObjectNode;

/**
 * Params of a {@code notifications/message} log notification.
 *
 * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577) along with the Logging
 *     feature; remains in the specification for at least twelve months. The spec's suggested
 *     migration is logging to stderr (stdio) or OpenTelemetry.
 */
@Deprecated(since = "2026-07-28")
// SEP-2577 spec contract: LoggingLevel is deprecated alongside this type; the reference is
// required for 1:1 fidelity.
// java:S1133 — this deprecation is mandated by the spec, not scheduled for our removal:
// MCP 2026-07-28 still defines the type and SEP-2577 holds it for a 12-month window, and
// mocapi-model mirrors schema.ts 1:1 (ADR-0014). Removing it would make mocapi a less
// faithful implementation. Revisit when the spec drops it, not before.
@SuppressWarnings({"deprecation", "java:S1133"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoggingMessageNotificationParams(
    LoggingLevel level, String logger, Object data, @JsonProperty("_meta") ObjectNode meta) {}
