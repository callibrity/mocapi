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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpHeaderValidatorTest {

  private final McpHeaderValidator validator = new McpHeaderValidator();

  private static ObjectNode paramsWithEnvelope() {
    ObjectNode params = JsonNodeFactory.instance.objectNode();
    ObjectNode meta = params.putObject("_meta");
    meta.put(McpMetaKeys.PROTOCOL_VERSION, McpServer.PROTOCOL_VERSION);
    meta.putObject(McpMetaKeys.CLIENT_INFO).put("name", "test-client").put("version", "1.0");
    meta.putObject(McpMetaKeys.CLIENT_CAPABILITIES);
    return params;
  }

  private static JsonRpcCall call(String method, ObjectNode params) {
    return JsonRpcCall.of(method, params, JsonNodeFactory.instance.numberNode(1));
  }

  /** Like {@link #call}, but accepts any {@code JsonNode} so malformed body shapes can be sent. */
  private static JsonRpcCall rawCall(String method, JsonNode params) {
    return JsonRpcCall.of(method, params, JsonNodeFactory.instance.numberNode(1));
  }

  private static HttpHeaders validHeadersFor(String method) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(McpHeaderValidator.MCP_PROTOCOL_VERSION_HEADER, McpServer.PROTOCOL_VERSION);
    headers.set(McpHeaderValidator.MCP_METHOD_HEADER, method);
    return headers;
  }

  @Nested
  class Protocol_version_header {

    @Test
    void valid_when_it_matches_the_body_envelope() {
      var result =
          validator.validate(
              validHeadersFor("tools/list"), call("tools/list", paramsWithEnvelope()));

      assertThat(result).isEmpty();
    }

    @Test
    void missing_header_fails() {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.remove(McpHeaderValidator.MCP_PROTOCOL_VERSION_HEADER);

      var result = validator.validate(headers, call("tools/list", paramsWithEnvelope()));

      assertThat(result)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("MCP-Protocol-Version"));
    }

    @Test
    void mismatched_header_fails() {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set(McpHeaderValidator.MCP_PROTOCOL_VERSION_HEADER, "2025-11-25");

      var result = validator.validate(headers, call("tools/list", paramsWithEnvelope()));

      assertThat(result)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("2025-11-25"));
    }

    @Test
    void missing_body_envelope_is_not_a_header_failure() {
      // The body-side failure belongs to the server's envelope validation (-32602).
      var result =
          validator.validate(
              validHeadersFor("tools/list"),
              call("tools/list", JsonNodeFactory.instance.objectNode()));

      assertThat(result).isEmpty();
    }

    @Test
    void header_lookup_is_case_insensitive() {
      HttpHeaders headers = new HttpHeaders();
      headers.set("mcp-protocol-version", McpServer.PROTOCOL_VERSION);
      headers.set("mcp-method", "tools/list");

      var result = validator.validate(headers, call("tools/list", paramsWithEnvelope()));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class Method_header {

    @Test
    void valid_when_it_matches_the_body_method() {
      var result =
          validator.validate(
              validHeadersFor("prompts/list"), call("prompts/list", paramsWithEnvelope()));

      assertThat(result).isEmpty();
    }

    @Test
    void missing_header_fails() {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.remove(McpHeaderValidator.MCP_METHOD_HEADER);

      var result = validator.validate(headers, call("tools/list", paramsWithEnvelope()));

      assertThat(result)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("Mcp-Method"));
    }

    @Test
    void mismatched_header_fails() {
      HttpHeaders headers = validHeadersFor("prompts/list");

      var result = validator.validate(headers, call("tools/list", paramsWithEnvelope()));

      assertThat(result)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("prompts/list"));
    }

    @Test
    void applies_to_notifications_too() {
      HttpHeaders headers = validHeadersFor("notifications/progress");
      var notification =
          JsonRpcNotification.of("notifications/cancelled", JsonNodeFactory.instance.objectNode());

      var result = validator.validate(headers, notification);

      assertThat(result).hasValueSatisfying(msg -> assertThat(msg).startsWith("HeaderMismatch"));
    }
  }

  /**
   * Malformed or absent body fields must not crash the validator or produce a spurious rejection.
   *
   * <p>The routing headers are authoritative; the body is only consulted to detect a
   * <em>contradiction</em> between the two. A body that supplies nothing usable — absent params, a
   * non-object params, a missing or non-object {@code _meta}, or a field of the wrong JSON type —
   * yields "no opinion", so validation passes on the headers alone. These shapes come off the wire
   * from untrusted clients, so each defensive branch is pinned rather than assumed.
   */
  @Nested
  class Malformed_body_params {

    @Test
    void absent_params_leaves_the_protocol_version_unchecked() {
      var result = validator.validate(validHeadersFor("tools/list"), rawCall("tools/list", null));

      assertThat(result).isEmpty();
    }

    @Test
    void non_object_params_leaves_the_protocol_version_unchecked() {
      var call = rawCall("tools/list", JsonNodeFactory.instance.stringNode("not-an-object"));

      var result = validator.validate(validHeadersFor("tools/list"), call);

      assertThat(result).isEmpty();
    }

    @Test
    void params_without_a_meta_envelope_leaves_the_protocol_version_unchecked() {
      var call = rawCall("tools/list", JsonNodeFactory.instance.objectNode());

      var result = validator.validate(validHeadersFor("tools/list"), call);

      assertThat(result).isEmpty();
    }

    @Test
    void non_object_meta_leaves_the_protocol_version_unchecked() {
      ObjectNode params = JsonNodeFactory.instance.objectNode();
      params.put("_meta", "not-an-object");

      var result = validator.validate(validHeadersFor("tools/list"), rawCall("tools/list", params));

      assertThat(result).isEmpty();
    }

    @Test
    void non_string_protocol_version_in_the_body_leaves_it_unchecked() {
      ObjectNode params = JsonNodeFactory.instance.objectNode();
      params.putObject("_meta").put(McpMetaKeys.PROTOCOL_VERSION, 20260728);

      var result = validator.validate(validHeadersFor("tools/list"), rawCall("tools/list", params));

      assertThat(result).isEmpty();
    }

    @Test
    void meta_without_a_protocol_version_key_leaves_it_unchecked() {
      ObjectNode params = JsonNodeFactory.instance.objectNode();
      params.putObject("_meta").put("something-else", "value");

      var result = validator.validate(validHeadersFor("tools/list"), rawCall("tools/list", params));

      assertThat(result).isEmpty();
    }

    @Test
    void non_object_params_leaves_the_name_unchecked_on_a_named_method() {
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");
      var call = rawCall("tools/call", JsonNodeFactory.instance.stringNode("not-an-object"));

      var result = validator.validate(headers, call);

      assertThat(result).isEmpty();
    }

    @Test
    void absent_params_leaves_the_name_unchecked_on_a_named_method() {
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");

      var result = validator.validate(headers, rawCall("tools/call", null));

      assertThat(result).isEmpty();
    }

    @Test
    void non_string_name_in_the_body_leaves_the_name_unchecked() {
      ObjectNode params = paramsWithEnvelope();
      params.put("name", 42);
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");

      var result = validator.validate(headers, rawCall("tools/call", params));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class Name_header {

    @Test
    void valid_when_it_matches_tools_call_params_name() {
      ObjectNode params = paramsWithEnvelope();
      params.put("name", "echo");
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");

      var result = validator.validate(headers, call("tools/call", params));

      assertThat(result).isEmpty();
    }

    @Test
    void missing_header_fails_on_tools_call() {
      ObjectNode params = paramsWithEnvelope();
      params.put("name", "echo");

      var result = validator.validate(validHeadersFor("tools/call"), call("tools/call", params));

      assertThat(result)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("Mcp-Name"));
    }

    @Test
    void mismatched_header_fails_on_tools_call() {
      ObjectNode params = paramsWithEnvelope();
      params.put("name", "echo");
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "other");

      var result = validator.validate(headers, call("tools/call", params));

      assertThat(result)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("other"));
    }

    @Test
    void missing_header_fails_on_prompts_get() {
      ObjectNode params = paramsWithEnvelope();
      params.put("name", "greeting");

      var result = validator.validate(validHeadersFor("prompts/get"), call("prompts/get", params));

      assertThat(result)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("Mcp-Name"));
    }

    @Test
    void resources_read_compares_against_params_uri() {
      ObjectNode params = paramsWithEnvelope();
      params.put("uri", "file:///a.txt");
      HttpHeaders headers = validHeadersFor("resources/read");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "file:///b.txt");

      var result = validator.validate(headers, call("resources/read", params));

      assertThat(result)
          .hasValueSatisfying(msg -> assertThat(msg).startsWith("HeaderMismatch").contains("uri"));
    }

    @Test
    void resources_read_valid_when_uri_matches() {
      ObjectNode params = paramsWithEnvelope();
      params.put("uri", "file:///a.txt");
      HttpHeaders headers = validHeadersFor("resources/read");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "file:///a.txt");

      var result = validator.validate(headers, call("resources/read", params));

      assertThat(result).isEmpty();
    }

    @Test
    void not_expected_on_other_methods() {
      var result =
          validator.validate(
              validHeadersFor("tools/list"), call("tools/list", paramsWithEnvelope()));

      assertThat(result).isEmpty();
    }

    @Test
    void stray_name_header_on_other_methods_is_ignored() {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "stray");

      var result = validator.validate(headers, call("tools/list", paramsWithEnvelope()));

      assertThat(result).isEmpty();
    }

    @Test
    void missing_body_name_is_not_a_header_failure() {
      // params.name missing is the server's invalid-params problem, not a header mismatch.
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");

      var result = validator.validate(headers, call("tools/call", paramsWithEnvelope()));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class Contributed_methods {

    @Test
    void contributed_method_requires_and_validates_mcp_name() {
      var validator = new McpHeaderValidator(Map.of("tasks/get", "taskId"));

      ObjectNode params = paramsWithEnvelope();
      params.put("taskId", "task-1");

      var missingHeaderResult =
          validator.validate(validHeadersFor("tasks/get"), call("tasks/get", params));

      assertThat(missingHeaderResult)
          .hasValueSatisfying(
              msg ->
                  assertThat(msg)
                      .startsWith("HeaderMismatch")
                      .contains("Mcp-Name")
                      .contains("tasks/get"));

      HttpHeaders matchingHeaders = validHeadersFor("tasks/get");
      matchingHeaders.set(McpHeaderValidator.MCP_NAME_HEADER, "task-1");

      var matchingResult = validator.validate(matchingHeaders, call("tasks/get", params));

      assertThat(matchingResult).isEmpty();

      HttpHeaders mismatchedHeaders = validHeadersFor("tasks/get");
      mismatchedHeaders.set(McpHeaderValidator.MCP_NAME_HEADER, "task-2");

      var mismatchedResult = validator.validate(mismatchedHeaders, call("tasks/get", params));

      assertThat(mismatchedResult)
          .hasValueSatisfying(
              msg -> assertThat(msg).startsWith("HeaderMismatch").contains("task-2"));
    }

    @Test
    void built_in_methods_unaffected_by_contributions() {
      var validator = new McpHeaderValidator(Map.of("tasks/get", "taskId"));

      ObjectNode params = paramsWithEnvelope();
      params.put("name", "echo");
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");

      var result = validator.validate(headers, call("tools/call", params));

      assertThat(result).isEmpty();
    }
  }

  /**
   * Pins the transport's header-mismatch constants to their literal wire values.
   *
   * <p>java:S3415 is suppressed because the assertions are not swapped. The named constant is the
   * actual value under test and the literal is the expected wire code, which is the correct AssertJ
   * order. Literals are deliberately spelled out so that changing a constant fails this test rather
   * than silently altering the wire protocol.
   */
  @Nested
  @SuppressWarnings("java:S3415")
  class Constants {

    @Test
    void header_mismatch_code_is_minus_32001() {
      assertThat(McpHeaderValidator.HEADER_MISMATCH_CODE).isEqualTo(-32020);
    }

    @Test
    void header_mismatch_name_is_spec_spelling() {
      assertThat(McpHeaderValidator.HEADER_MISMATCH_NAME).isEqualTo("HeaderMismatch");
    }
  }
}
