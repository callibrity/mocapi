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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Round-trips for the types introduced by the MCP 2026-07-28 revision. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class Mcp20260728TypesSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Nested
  class Discover_result {

    @Test
    void round_trips_all_required_fields() throws Exception {
      var original =
          new DiscoverResult(
              List.of("2026-07-28"),
              new ServerCapabilities(
                  null, new ToolsCapability(false), null, null, null, null, Map.of()),
              new Implementation("mocapi", null, "1.0.0", null),
              "An example server",
              60000L,
              CacheScope.PUBLIC,
              ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"supportedVersions\":[\"2026-07-28\"]")
          .contains("\"serverInfo\":{\"name\":\"mocapi\"")
          .contains("\"instructions\":\"An example server\"")
          .contains("\"ttlMs\":60000")
          .contains("\"cacheScope\":\"public\"")
          .contains("\"resultType\":\"complete\"");

      var deserialized = mapper.readValue(json, DiscoverResult.class);
      assertThat(deserialized.supportedVersions()).containsExactly("2026-07-28");
      assertThat(deserialized.capabilities().tools().listChanged()).isFalse();
      assertThat(deserialized.serverInfo().name()).isEqualTo("mocapi");
    }
  }

  @Nested
  class Input_required_result {

    @Test
    void round_trips_an_embedded_elicit_request() throws Exception {
      var original =
          new InputRequiredResult(
              Map.of("elicit-1", new ElicitRequest(new ElicitRequestFormParams("Confirm?", null))),
              "opaque-state",
              ResultTypes.INPUT_REQUIRED);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"resultType\":\"input_required\"")
          .contains("\"method\":\"elicitation/create\"")
          .contains("\"message\":\"Confirm?\"")
          .contains("\"requestState\":\"opaque-state\"");

      var deserialized = mapper.readValue(json, InputRequiredResult.class);
      assertThat(deserialized.inputRequests().get("elicit-1"))
          .isInstanceOfSatisfying(
              ElicitRequest.class,
              req ->
                  assertThat(req.params())
                      .isInstanceOfSatisfying(
                          ElicitRequestFormParams.class,
                          p -> assertThat(p.message()).isEqualTo("Confirm?")));
      assertThat(deserialized.requestState()).isEqualTo("opaque-state");
    }

    @Test
    void supports_the_request_state_only_variant() throws Exception {
      var original = new InputRequiredResult(null, "state-only", ResultTypes.INPUT_REQUIRED);
      String json = mapper.writeValueAsString(original);
      assertThat(json).doesNotContain("inputRequests");

      var deserialized = mapper.readValue(json, InputRequiredResult.class);
      assertThat(deserialized.inputRequests()).isNull();
      assertThat(deserialized.requestState()).isEqualTo("state-only");
    }
  }

  @Nested
  // SEP-2577 spec contract: the InputResponse union retains the deprecated sampling/roots result
  // shapes for the deprecation window; deduction across all three members must keep working.
  @SuppressWarnings("deprecation")
  class Input_response_deduction {

    @Test
    void an_action_property_deduces_elicit_result() throws Exception {
      var deserialized = mapper.readValue("{\"action\":\"decline\"}", InputResponse.class);
      assertThat(deserialized)
          .isInstanceOfSatisfying(
              ElicitResult.class, r -> assertThat(r.action()).isEqualTo(ElicitAction.DECLINE));
    }

    @Test
    void a_roots_property_deduces_list_roots_result() throws Exception {
      var deserialized =
          mapper.readValue(
              "{\"roots\":[{\"uri\":\"file:///home\",\"name\":\"home\"}]}", InputResponse.class);
      assertThat(deserialized)
          .isInstanceOfSatisfying(
              ListRootsResult.class,
              r -> assertThat(r.roots().getFirst().uri()).isEqualTo("file:///home"));
    }

    @Test
    void role_and_model_properties_deduce_create_message_result() throws Exception {
      var deserialized =
          mapper.readValue(
              "{\"role\":\"assistant\",\"content\":{\"type\":\"text\",\"text\":\"hi\"},"
                  + "\"model\":\"example-model\"}",
              InputResponse.class);
      assertThat(deserialized)
          .isInstanceOfSatisfying(
              CreateMessageResult.class,
              r -> {
                assertThat(r.model()).isEqualTo("example-model");
                assertThat(r.text()).isEqualTo("hi");
              });
    }
  }

  @Nested
  class Error_data_payloads {

    @Test
    void unsupported_protocol_version_data_round_trip() throws Exception {
      var original = new UnsupportedProtocolVersionErrorData(List.of("2026-07-28"), "2025-11-25");
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"supported\":[\"2026-07-28\"]")
          .contains("\"requested\":\"2025-11-25\"");

      var deserialized = mapper.readValue(json, UnsupportedProtocolVersionErrorData.class);
      assertThat(deserialized.supported()).containsExactly("2026-07-28");
      assertThat(deserialized.requested()).isEqualTo("2025-11-25");
    }

    @Test
    void missing_required_client_capability_data_round_trip() throws Exception {
      var original =
          new MissingRequiredClientCapabilityErrorData(
              new ClientCapabilities(
                  null, null, null, new ElicitationCapability(null, null), null));
      String json = mapper.writeValueAsString(original);
      assertThat(json).contains("\"requiredCapabilities\":{\"elicitation\":{}}");

      var deserialized = mapper.readValue(json, MissingRequiredClientCapabilityErrorData.class);
      assertThat(deserialized.requiredCapabilities().elicitation()).isNotNull();
    }
  }

  @Nested
  class Subscriptions_family {

    @Test
    void listen_request_params_round_trip() throws Exception {
      var original =
          new SubscriptionsListenRequestParams(
              new SubscriptionFilter(true, null, true, List.of("file:///watched.txt")), null);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"toolsListChanged\":true")
          .doesNotContain("promptsListChanged")
          .contains("\"resourceSubscriptions\":[\"file:///watched.txt\"]");

      var deserialized = mapper.readValue(json, SubscriptionsListenRequestParams.class);
      assertThat(deserialized.notifications().toolsListChanged()).isTrue();
      assertThat(deserialized.notifications().resourceSubscriptions())
          .containsExactly("file:///watched.txt");
    }

    @Test
    void acknowledged_notification_params_round_trip() throws Exception {
      var original =
          new SubscriptionsAcknowledgedNotificationParams(
              new SubscriptionFilter(true, false, null, null), null);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"toolsListChanged\":true")
          .contains("\"promptsListChanged\":false");

      var deserialized = mapper.readValue(json, SubscriptionsAcknowledgedNotificationParams.class);
      assertThat(deserialized.notifications().promptsListChanged()).isFalse();
    }
  }

  @Nested
  class Cache_scope {

    @Test
    void wire_values_are_lowercase() throws Exception {
      assertThat(mapper.writeValueAsString(CacheScope.PUBLIC)).isEqualTo("\"public\"");
      assertThat(mapper.writeValueAsString(CacheScope.PRIVATE)).isEqualTo("\"private\"");
      assertThat(mapper.readValue("\"public\"", CacheScope.class)).isEqualTo(CacheScope.PUBLIC);
      assertThat(mapper.readValue("\"private\"", CacheScope.class)).isEqualTo(CacheScope.PRIVATE);
    }
  }
}
