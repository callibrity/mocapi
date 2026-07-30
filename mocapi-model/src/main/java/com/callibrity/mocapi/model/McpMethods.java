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

// java:S1133 — this deprecation is mandated by the spec, not scheduled for our removal:
// MCP 2026-07-28 still defines the type and SEP-2577 holds it for a 12-month window, and
// mocapi-model mirrors schema.ts 1:1 (ADR-0014). Removing it would make mocapi a less
// faithful implementation. Revisit when the spec drops it, not before.
@SuppressWarnings("java:S1133")
public final class McpMethods {

  public static final String SERVER_DISCOVER = "server/discover";
  public static final String TOOLS_LIST = "tools/list";
  public static final String TOOLS_CALL = "tools/call";
  public static final String PROMPTS_LIST = "prompts/list";
  public static final String PROMPTS_GET = "prompts/get";
  public static final String RESOURCES_LIST = "resources/list";
  public static final String RESOURCES_TEMPLATES_LIST = "resources/templates/list";
  public static final String RESOURCES_READ = "resources/read";
  public static final String COMPLETION_COMPLETE = "completion/complete";
  public static final String SUBSCRIPTIONS_LISTEN = "subscriptions/listen";
  public static final String ELICITATION_CREATE = "elicitation/create";

  /**
   * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Sampling is no longer a
   *     JSON-RPC request; this string survives only as the {@code method} discriminator of the
   *     embedded {@link CreateMessageRequest} {@link InputRequest} union member.
   */
  @Deprecated(since = "2026-07-28")
  public static final String SAMPLING_CREATE_MESSAGE = "sampling/createMessage";

  /**
   * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Roots listing is no longer
   *     a JSON-RPC request; this string survives only as the {@code method} discriminator of the
   *     embedded {@link ListRootsRequest} {@link InputRequest} union member.
   */
  @Deprecated(since = "2026-07-28")
  public static final String ROOTS_LIST = "roots/list";

  public static final String NOTIFICATIONS_CANCELLED = "notifications/cancelled";
  public static final String NOTIFICATIONS_PROGRESS = "notifications/progress";

  /**
   * @deprecated Deprecated as of protocol version 2026-07-28 (SEP-2577). Clients should rely on
   *     OpenTelemetry-style logging integrations instead of {@code notifications/message}; the
   *     notification remains in the specification for at least twelve months.
   */
  @Deprecated(since = "2026-07-28")
  public static final String NOTIFICATIONS_MESSAGE = "notifications/message";

  public static final String NOTIFICATIONS_RESOURCES_LIST_CHANGED =
      "notifications/resources/list_changed";
  public static final String NOTIFICATIONS_RESOURCES_UPDATED = "notifications/resources/updated";
  public static final String NOTIFICATIONS_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
  public static final String NOTIFICATIONS_PROMPTS_LIST_CHANGED =
      "notifications/prompts/list_changed";
  public static final String NOTIFICATIONS_ELICITATION_COMPLETE =
      "notifications/elicitation/complete";
  public static final String NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED =
      "notifications/subscriptions/acknowledged";

  private McpMethods() {}
}
