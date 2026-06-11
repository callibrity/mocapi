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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpMethodsTest {

  @Nested
  class Request_methods {

    @Test
    void pin_spec_method_strings() {
      assertThat(McpMethods.SERVER_DISCOVER).isEqualTo("server/discover");
      assertThat(McpMethods.TOOLS_LIST).isEqualTo("tools/list");
      assertThat(McpMethods.TOOLS_CALL).isEqualTo("tools/call");
      assertThat(McpMethods.PROMPTS_LIST).isEqualTo("prompts/list");
      assertThat(McpMethods.PROMPTS_GET).isEqualTo("prompts/get");
      assertThat(McpMethods.RESOURCES_LIST).isEqualTo("resources/list");
      assertThat(McpMethods.RESOURCES_TEMPLATES_LIST).isEqualTo("resources/templates/list");
      assertThat(McpMethods.RESOURCES_READ).isEqualTo("resources/read");
      assertThat(McpMethods.COMPLETION_COMPLETE).isEqualTo("completion/complete");
      assertThat(McpMethods.SUBSCRIPTIONS_LISTEN).isEqualTo("subscriptions/listen");
      assertThat(McpMethods.ELICITATION_CREATE).isEqualTo("elicitation/create");
    }

    @Test
    // Deprecated as of protocol version 2026-07-28 (SEP-2577); the spec retains these embedded
    // InputRequest method strings for at least twelve months, so mocapi-model keeps the constants.
    @SuppressWarnings("deprecation")
    void pin_deprecated_embedded_input_request_method_strings() {
      assertThat(McpMethods.SAMPLING_CREATE_MESSAGE).isEqualTo("sampling/createMessage");
      assertThat(McpMethods.ROOTS_LIST).isEqualTo("roots/list");
    }
  }

  @Nested
  class Notification_methods {

    @Test
    void pin_spec_notification_strings() {
      assertThat(McpMethods.NOTIFICATIONS_CANCELLED).isEqualTo("notifications/cancelled");
      assertThat(McpMethods.NOTIFICATIONS_PROGRESS).isEqualTo("notifications/progress");
      assertThat(McpMethods.NOTIFICATIONS_RESOURCES_LIST_CHANGED)
          .isEqualTo("notifications/resources/list_changed");
      assertThat(McpMethods.NOTIFICATIONS_RESOURCES_UPDATED)
          .isEqualTo("notifications/resources/updated");
      assertThat(McpMethods.NOTIFICATIONS_TOOLS_LIST_CHANGED)
          .isEqualTo("notifications/tools/list_changed");
      assertThat(McpMethods.NOTIFICATIONS_PROMPTS_LIST_CHANGED)
          .isEqualTo("notifications/prompts/list_changed");
      assertThat(McpMethods.NOTIFICATIONS_ELICITATION_COMPLETE)
          .isEqualTo("notifications/elicitation/complete");
      assertThat(McpMethods.NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED)
          .isEqualTo("notifications/subscriptions/acknowledged");
    }

    @Test
    // Deprecated as of protocol version 2026-07-28 (SEP-2577); the spec retains
    // notifications/message for at least twelve months, so mocapi-model keeps the constant.
    @SuppressWarnings("deprecation")
    void pin_deprecated_logging_notification_string() {
      assertThat(McpMethods.NOTIFICATIONS_MESSAGE).isEqualTo("notifications/message");
    }
  }
}
