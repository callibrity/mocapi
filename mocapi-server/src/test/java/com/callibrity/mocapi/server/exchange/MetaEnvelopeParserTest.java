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
package com.callibrity.mocapi.server.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MetaEnvelopeParserTest {

  private final MetaEnvelopeParser parser = new MetaEnvelopeParser(new ObjectMapper());

  private static ObjectNode validParams() {
    ObjectNode params = JsonNodeFactory.instance.objectNode();
    ObjectNode meta = params.putObject("_meta");
    meta.put(McpMetaKeys.PROTOCOL_VERSION, McpServer.PROTOCOL_VERSION);
    ObjectNode clientInfo = meta.putObject(McpMetaKeys.CLIENT_INFO);
    clientInfo.put("name", "test-client");
    clientInfo.put("version", "1.0.0");
    meta.putObject(McpMetaKeys.CLIENT_CAPABILITIES);
    return params;
  }

  private static ObjectNode meta(ObjectNode params) {
    return (ObjectNode) params.get("_meta");
  }

  @Nested
  class When_envelope_is_valid {

    @Test
    void returns_exchange_with_protocol_version_client_info_and_capabilities() {
      McpExchange exchange = parser.parse(validParams());

      assertThat(exchange.protocolVersion()).isEqualTo(McpServer.PROTOCOL_VERSION);
      assertThat(exchange.clientInfo().name()).isEqualTo("test-client");
      assertThat(exchange.clientInfo().version()).isEqualTo("1.0.0");
      assertThat(exchange.clientCapabilities()).isNotNull();
    }

    @Test
    void tolerates_other_request_params_beside_the_envelope() {
      ObjectNode params = validParams();
      params.put("name", "some-tool");
      params.putObject("arguments").put("input", "x");

      assertThat(parser.parse(params).protocolVersion()).isEqualTo(McpServer.PROTOCOL_VERSION);
    }

    @Test
    void bare_elicitation_capability_means_form_support() {
      ObjectNode params = validParams();
      ((ObjectNode) meta(params).get(McpMetaKeys.CLIENT_CAPABILITIES)).putObject("elicitation");

      assertThat(parser.parse(params).supportsElicitationForm()).isTrue();
    }

    @Test
    void envelope_without_clientInfo_parses_successfully() {
      ObjectNode params = validParams();
      meta(params).remove(McpMetaKeys.CLIENT_INFO);

      McpExchange exchange = parser.parse(params);

      assertThat(exchange.protocolVersion()).isEqualTo(McpServer.PROTOCOL_VERSION);
      assertThat(exchange.clientInfo()).isNull();
      assertThat(exchange.clientCapabilities()).isNotNull();
    }

    @Test
    void present_but_malformed_clientInfo_still_fails() {
      ObjectNode params = validParams();
      ((ObjectNode) meta(params).get(McpMetaKeys.CLIENT_INFO)).remove("version");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS);
    }
  }

  @Nested
  class When_envelope_is_missing {

    @Test
    void null_params_is_invalid_params() {
      assertThatThrownBy(() -> parser.parse(null))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("_meta");
    }

    @Test
    void non_object_params_is_invalid_params() {
      var stringParams = JsonNodeFactory.instance.stringNode("nope");

      assertThatThrownBy(() -> parser.parse(stringParams))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS);
    }

    @Test
    void params_without_meta_is_invalid_params() {
      ObjectNode params = JsonNodeFactory.instance.objectNode().put("name", "some-tool");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("_meta");
    }

    @Test
    void non_object_meta_is_invalid_params() {
      ObjectNode params = JsonNodeFactory.instance.objectNode();
      params.put("_meta", "not-an-object");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS);
    }
  }

  @Nested
  class When_required_keys_are_missing {

    @Test
    void missing_protocol_version_is_invalid_params() {
      ObjectNode params = validParams();
      meta(params).remove(McpMetaKeys.PROTOCOL_VERSION);

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining(McpMetaKeys.PROTOCOL_VERSION);
    }

    @Test
    void missing_client_capabilities_is_invalid_params() {
      ObjectNode params = validParams();
      meta(params).remove(McpMetaKeys.CLIENT_CAPABILITIES);

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining(McpMetaKeys.CLIENT_CAPABILITIES);
    }
  }

  @Nested
  class When_keys_are_malformed {

    @Test
    void non_string_protocol_version_is_invalid_params() {
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.PROTOCOL_VERSION, 42);

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining(McpMetaKeys.PROTOCOL_VERSION);
    }

    @Test
    void non_object_client_info_is_invalid_params() {
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.CLIENT_INFO, "not-an-object");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining(McpMetaKeys.CLIENT_INFO);
    }

    @Test
    void client_info_without_name_is_invalid_params() {
      ObjectNode params = validParams();
      ((ObjectNode) meta(params).get(McpMetaKeys.CLIENT_INFO)).remove("name");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining("name and version are required");
    }

    @Test
    void client_info_without_version_is_invalid_params() {
      ObjectNode params = validParams();
      ((ObjectNode) meta(params).get(McpMetaKeys.CLIENT_INFO)).remove("version");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS);
    }

    @Test
    void non_object_client_capabilities_is_invalid_params() {
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.CLIENT_CAPABILITIES, "not-an-object");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS)
          .hasMessageContaining(McpMetaKeys.CLIENT_CAPABILITIES);
    }
  }

  @Nested
  class When_version_is_unsupported {

    @Test
    void well_formed_envelope_with_unknown_version_raises_unsupported_protocol_version() {
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.PROTOCOL_VERSION, "2025-11-25");

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(UnsupportedProtocolVersionException.class)
          .satisfies(
              e -> {
                var data = ((UnsupportedProtocolVersionException) e).data();
                assertThat(data.supported()).containsExactly(McpServer.PROTOCOL_VERSION);
                assertThat(data.requested()).isEqualTo("2025-11-25");
              });
    }

    @Test
    void malformed_envelope_wins_over_unsupported_version() {
      // S10: a malformed envelope is -32602 even when the version it carries is also unsupported —
      // the version check only runs once the envelope has proven well-formed.
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.PROTOCOL_VERSION, "2025-11-25");
      meta(params).remove(McpMetaKeys.CLIENT_CAPABILITIES);

      assertThatThrownBy(() -> parser.parse(params))
          .isInstanceOf(JsonRpcException.class)
          .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcProtocol.INVALID_PARAMS);
    }
  }

  @Nested
  class Trace_context_keys {

    private static final String TRACEPARENT =
        "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

    @Test
    void absent_trace_keys_yield_the_empty_trace_context() {
      McpExchange exchange = parser.parse(validParams());

      assertThat(exchange.traceContext()).isEqualTo(TraceContext.NONE);
      assertThat(exchange.traceContext().isPresent()).isFalse();
    }

    @Test
    void unprefixed_traceparent_tracestate_and_baggage_are_parsed() {
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.TRACEPARENT, TRACEPARENT);
      meta(params).put(McpMetaKeys.TRACESTATE, "congo=t61rcWkgMzE");
      meta(params).put(McpMetaKeys.BAGGAGE, "userId=alice");

      TraceContext traceContext = parser.parse(params).traceContext();

      assertThat(traceContext.isPresent()).isTrue();
      assertThat(traceContext.traceparent()).isEqualTo(TRACEPARENT);
      assertThat(traceContext.tracestate()).isEqualTo("congo=t61rcWkgMzE");
      assertThat(traceContext.baggage()).isEqualTo("userId=alice");
    }

    @Test
    void traceparent_alone_is_present_with_null_siblings() {
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.TRACEPARENT, TRACEPARENT);

      TraceContext traceContext = parser.parse(params).traceContext();

      assertThat(traceContext.isPresent()).isTrue();
      assertThat(traceContext.tracestate()).isNull();
      assertThat(traceContext.baggage()).isNull();
    }

    @Test
    void non_string_trace_keys_are_treated_as_absent_not_rejected() {
      // Observability hints must never fail the request.
      ObjectNode params = validParams();
      meta(params).put(McpMetaKeys.TRACEPARENT, 42);

      McpExchange exchange = parser.parse(params);

      assertThat(exchange.traceContext()).isEqualTo(TraceContext.NONE);
    }
  }
}
