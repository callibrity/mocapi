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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpMetaKeysTest {

  @Nested
  class Prefixed_keys {

    @Test
    void pin_required_request_envelope_keys() {
      assertThat(McpMetaKeys.PROTOCOL_VERSION).isEqualTo("io.modelcontextprotocol/protocolVersion");
      assertThat(McpMetaKeys.CLIENT_INFO).isEqualTo("io.modelcontextprotocol/clientInfo");
      assertThat(McpMetaKeys.CLIENT_CAPABILITIES)
          .isEqualTo("io.modelcontextprotocol/clientCapabilities");
    }

    @Test
    void pin_subscription_id_key() {
      assertThat(McpMetaKeys.SUBSCRIPTION_ID).isEqualTo("io.modelcontextprotocol/subscriptionId");
    }
  }

  @Nested
  class Unprefixed_keys {

    @Test
    void pin_progress_token_key() {
      assertThat(McpMetaKeys.PROGRESS_TOKEN).isEqualTo("progressToken");
    }

    @Test
    void pin_trace_context_keys() {
      assertThat(McpMetaKeys.TRACEPARENT).isEqualTo("traceparent");
      assertThat(McpMetaKeys.TRACESTATE).isEqualTo("tracestate");
      assertThat(McpMetaKeys.BAGGAGE).isEqualTo("baggage");
    }
  }
}
