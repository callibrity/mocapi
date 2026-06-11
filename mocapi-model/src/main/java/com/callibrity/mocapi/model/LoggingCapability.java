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

/**
 * The server's logging capability marker.
 *
 * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577) along with the Logging
 *     feature; remains in the specification for at least twelve months. The spec's suggested
 *     migration is logging to stderr (stdio) or OpenTelemetry.
 */
@Deprecated(since = "2026-07-28")
// Empty by design — MCP spec defines this as a marker object whose presence signals capability.
@SuppressWarnings("java:S2094")
public record LoggingCapability() {}
