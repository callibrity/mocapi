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

public final class McpMethods {

  public static final String INITIALIZE = "initialize";
  public static final String PING = "ping";
  public static final String TOOLS_LIST = "tools/list";
  public static final String TOOLS_CALL = "tools/call";
  public static final String PROMPTS_LIST = "prompts/list";
  public static final String PROMPTS_GET = "prompts/get";
  public static final String RESOURCES_LIST = "resources/list";
  public static final String RESOURCES_TEMPLATES_LIST = "resources/templates/list";
  public static final String RESOURCES_READ = "resources/read";
  public static final String RESOURCES_SUBSCRIBE = "resources/subscribe";
  public static final String RESOURCES_UNSUBSCRIBE = "resources/unsubscribe";
  public static final String LOGGING_SET_LEVEL = "logging/setLevel";
  public static final String COMPLETION_COMPLETE = "completion/complete";
  public static final String SAMPLING_CREATE_MESSAGE = "sampling/createMessage";
  public static final String ELICITATION_CREATE = "elicitation/create";
  public static final String ROOTS_LIST = "roots/list";

  public static final String NOTIFICATIONS_INITIALIZED = "notifications/initialized";
  public static final String NOTIFICATIONS_CANCELLED = "notifications/cancelled";
  public static final String NOTIFICATIONS_PROGRESS = "notifications/progress";
  public static final String NOTIFICATIONS_MESSAGE = "notifications/message";
  public static final String NOTIFICATIONS_RESOURCES_LIST_CHANGED =
      "notifications/resources/list_changed";
  public static final String NOTIFICATIONS_RESOURCES_UPDATED = "notifications/resources/updated";
  public static final String NOTIFICATIONS_TOOLS_LIST_CHANGED = "notifications/tools/list_changed";
  public static final String NOTIFICATIONS_PROMPTS_LIST_CHANGED =
      "notifications/prompts/list_changed";
  public static final String NOTIFICATIONS_ROOTS_LIST_CHANGED = "notifications/roots/list_changed";
  public static final String NOTIFICATIONS_ELICITATION_COMPLETE =
      "notifications/elicitation/complete";

  private McpMethods() {}
}
