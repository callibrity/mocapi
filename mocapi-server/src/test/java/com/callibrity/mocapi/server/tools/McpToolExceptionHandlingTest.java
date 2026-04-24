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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolException;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises how {@link McpToolsService} surfaces {@link McpToolException} (and its subclasses)
 * thrown from tool methods, both synchronously and asynchronously. The contract: message becomes a
 * text content block, structured payload becomes {@code structuredContent} (Jackson-serialized and
 * required to be a JSON object), additional content blocks are appended after the message, and
 * {@code isError} is always {@code true}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolExceptionHandlingTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);

  private McpToolsService serviceWith(Object bean) {
    var methods = MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class);
    var handlers =
        methods.stream()
            .map(m -> CallToolHandlers.build(bean, m, generator, mapper, List.of(), s -> s, false))
            .toList();
    return new McpToolsService(handlers, mapper, mock(McpResponseCorrelationService.class));
  }

  private CallToolRequestParams call(String name) {
    return new CallToolRequestParams(name, mapper.createObjectNode(), null, null);
  }

  @Nested
  class Synchronous_throw {

    @Test
    void message_only_produces_is_error_with_single_text_block_and_no_structured_content() {
      var service = serviceWith(new MessageOnlyTool());
      var result = service.callTool(call("message-only"));
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content()).hasSize(1);
      assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("plain failure");
    }

    @Test
    void structured_content_payload_is_serialized_into_structured_content_field() {
      var service = serviceWith(new StructuredErrorTool());
      var result = service.callTool(call("structured-error"));
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();
      assertThat(result.structuredContent().get("code").asString()).isEqualTo("NOT_FOUND");
      assertThat(result.structuredContent().get("detail").asString()).isEqualTo("no such user");
      assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("lookup failed");
    }

    @Test
    void additional_content_blocks_are_appended_after_the_message() {
      var service = serviceWith(new AdditionalContentTool());
      var result = service.callTool(call("additional-content"));
      assertThat(result.isError()).isTrue();
      assertThat(result.content()).hasSize(2);
      assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("something went wrong");
      assertThat(((TextContent) result.content().get(1)).text())
          .isEqualTo("see troubleshooting.md");
    }

    @Test
    void subclass_is_caught_as_the_parent_so_authors_can_extend_freely() {
      var service = serviceWith(new SubclassedErrorTool());
      var result = service.callTool(call("subclassed-error"));
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();
      assertThat(result.structuredContent().get("code").asString()).isEqualTo("USER_NOT_FOUND");
      assertThat(result.structuredContent().get("username").asString()).isEqualTo("alice");
    }

    @Test
    void list_payload_that_serializes_to_array_node_fails_with_illegal_state() {
      var service = serviceWith(new ListPayloadTool());
      var result = service.callTool(call("list-payload"));
      assertThat(result.isError()).isTrue();
      assertThat(((TextContent) result.content().getFirst()).text())
          .contains("must serialize to a JSON object");
    }

    @Test
    void string_payload_that_serializes_to_text_node_fails_with_illegal_state() {
      var service = serviceWith(new StringPayloadTool());
      var result = service.callTool(call("string-payload"));
      assertThat(result.isError()).isTrue();
      assertThat(((TextContent) result.content().getFirst()).text())
          .contains("must serialize to a JSON object");
    }

    @Test
    void numeric_payload_that_serializes_to_number_node_fails_with_illegal_state() {
      var service = serviceWith(new NumberPayloadTool());
      var result = service.callTool(call("number-payload"));
      assertThat(result.isError()).isTrue();
      assertThat(((TextContent) result.content().getFirst()).text())
          .contains("must serialize to a JSON object");
    }

    @Test
    void boolean_payload_that_serializes_to_boolean_node_fails_with_illegal_state() {
      var service = serviceWith(new BooleanPayloadTool());
      var result = service.callTool(call("boolean-payload"));
      assertThat(result.isError()).isTrue();
      assertThat(((TextContent) result.content().getFirst()).text())
          .contains("must serialize to a JSON object");
    }

    @Test
    void error_message_names_the_offending_class_and_node_type_to_help_diagnose() {
      // The error must be actionable: the author needs to know which field was wrong and what it
      // serialized to. Verifies the message names both the Java class and the JSON node type.
      var service = serviceWith(new StringPayloadTool());
      var result = service.callTool(call("string-payload"));
      String text = ((TextContent) result.content().getFirst()).text();
      // Jackson's node type for a TextNode is STRING.
      assertThat(text).contains("java.lang.String").contains("STRING");
    }

    @Test
    void null_message_falls_back_to_toString_so_the_text_block_is_still_populated() {
      // Exercises the `getMessage() != null ? getMessage() : toString()` branch in the catch arm.
      // Without the fallback we'd emit a null text block, which would fail CallToolResult
      // serialization. toString() on a message-less RuntimeException yields the class name.
      var service = serviceWith(new NullMessageTool());
      var result = service.callTool(call("null-message"));
      assertThat(result.isError()).isTrue();
      assertThat(result.content()).hasSize(1);
      assertThat(((TextContent) result.content().getFirst()).text())
          .contains(NullMessageTool.NullMessageException.class.getName());
    }

    @Test
    void null_additional_content_from_subclass_is_tolerated_and_skipped() {
      // Exercises the `additional != null && !additional.isEmpty()` guard. A subclass that
      // returns null from getAdditionalContent() must not produce an NPE — the guard skips the
      // addAll and we still emit a well-formed single-block result.
      var service = serviceWith(new NullAdditionalContentTool());
      var result = service.callTool(call("null-additional"));
      assertThat(result.isError()).isTrue();
      assertThat(result.content()).hasSize(1);
      assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("nope");
    }

    @Test
    void empty_additional_content_is_equivalent_to_no_additional_content() {
      // Default getAdditionalContent() returns List.of() — the guard's !isEmpty() check skips
      // addAll. Verifies the default behavior matches the "null additional" shape one-for-one.
      var service = serviceWith(new MessageOnlyTool());
      var result = service.callTool(call("message-only"));
      assertThat(result.content()).hasSize(1);
    }

    @Test
    void structured_content_and_additional_content_can_coexist() {
      // Covers the path where both optional fields are populated: message block, then
      // additional blocks, then structuredContent all land on the same result.
      var service = serviceWith(new BothStructuredAndAdditionalContentTool());
      var result = service.callTool(call("structured-plus-additional"));
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();
      assertThat(result.structuredContent().get("code").asString()).isEqualTo("COMBINED");
      assertThat(result.content()).hasSize(2);
      assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("combined failure");
      assertThat(((TextContent) result.content().get(1)).text()).isEqualTo("see also: FAQ");
    }
  }

  @Nested
  class Asynchronous_throw {

    @Test
    void failed_future_with_McpToolException_cause_surfaces_as_is_error_result() {
      var service = serviceWith(new AsyncStructuredErrorTool());
      var result = service.callTool(call("async-error"));
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();
      assertThat(result.structuredContent().get("code").asString()).isEqualTo("TIMEOUT");
      assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("took too long");
    }
  }

  @Nested
  class Non_McpToolException_paths_are_unaffected {

    @Test
    void plain_RuntimeException_still_becomes_message_only_is_error_result() {
      var service = serviceWith(new PlainRuntimeErrorTool());
      var result = service.callTool(call("plain-error"));
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content()).hasSize(1);
      assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("boom");
    }

    @Test
    void
        JsonRpcException_FORBIDDEN_is_NOT_caught_as_McpToolException_and_rethrows_at_protocol_level() {
      // McpToolException doesn't extend JsonRpcException, so the FORBIDDEN short-circuit path in
      // McpToolsService still applies to genuine JSON-RPC errors. Verifies the ordering of catch
      // arms: McpToolException, then JsonRpcException.
      var service = serviceWith(new ForbiddenTool());
      assertThatThrownBy(() -> service.callTool(call("forbidden")))
          .isInstanceOf(com.callibrity.ripcurl.core.exception.JsonRpcException.class);
    }
  }

  // --- test beans --------------------------------------------------------

  record ErrorDetails(String code, String detail) {}

  record WidgetResult(String value) {}

  static class MessageOnlyTool {
    @McpTool(name = "message-only")
    public WidgetResult fail() {
      throw new McpToolException("plain failure");
    }
  }

  static class StructuredErrorTool {
    @McpTool(name = "structured-error")
    public WidgetResult fail() {
      throw new McpToolException("lookup failed", new ErrorDetails("NOT_FOUND", "no such user"));
    }
  }

  static class AdditionalContentTool {
    @McpTool(name = "additional-content")
    public WidgetResult fail() {
      throw new McpToolException("something went wrong") {
        @Override
        public List<ContentBlock> getAdditionalContent() {
          return List.of(new TextContent("see troubleshooting.md", null));
        }
      };
    }
  }

  static class SubclassedErrorTool {
    @McpTool(name = "subclassed-error")
    public WidgetResult fail() {
      throw new UserNotFoundException("alice");
    }
  }

  static class ListPayloadTool {
    @McpTool(name = "list-payload")
    public WidgetResult fail() {
      throw new McpToolException("array payload", List.of("not", "an", "object"));
    }
  }

  static class StringPayloadTool {
    @McpTool(name = "string-payload")
    public WidgetResult fail() {
      throw new McpToolException("string payload", "just a string");
    }
  }

  static class NumberPayloadTool {
    @McpTool(name = "number-payload")
    public WidgetResult fail() {
      throw new McpToolException("number payload", 42);
    }
  }

  static class BooleanPayloadTool {
    @McpTool(name = "boolean-payload")
    public WidgetResult fail() {
      throw new McpToolException("boolean payload", Boolean.TRUE);
    }
  }

  static class AsyncStructuredErrorTool {
    @McpTool(name = "async-error")
    public CompletionStage<WidgetResult> fail() {
      return CompletableFuture.failedFuture(
          new McpToolException("took too long", new ErrorDetails("TIMEOUT", "exceeded deadline")));
    }
  }

  static class PlainRuntimeErrorTool {
    @McpTool(name = "plain-error")
    public WidgetResult fail() {
      throw new RuntimeException("boom");
    }
  }

  static class ForbiddenTool {
    @McpTool(name = "forbidden")
    public WidgetResult fail() {
      throw new com.callibrity.ripcurl.core.exception.JsonRpcException(
          com.callibrity.mocapi.server.JsonRpcErrorCodes.FORBIDDEN, "nope");
    }
  }

  static class NullMessageTool {
    @McpTool(name = "null-message")
    public WidgetResult fail() {
      throw new NullMessageException();
    }

    static class NullMessageException extends McpToolException {
      NullMessageException() {
        // Message-less constructor path — getMessage() will return null, exercising the
        // toString() fallback inside McpToolsService.toErrorCallToolResult.
        super(null);
      }
    }
  }

  static class NullAdditionalContentTool {
    @McpTool(name = "null-additional")
    public WidgetResult fail() {
      throw new McpToolException("nope") {
        @Override
        public List<ContentBlock> getAdditionalContent() {
          // Deliberately null — the handler must not NPE.
          return null;
        }
      };
    }
  }

  static class BothStructuredAndAdditionalContentTool {
    @McpTool(name = "structured-plus-additional")
    public WidgetResult fail() {
      throw new McpToolException("combined failure", new ErrorDetails("COMBINED", "both paths")) {
        @Override
        public List<ContentBlock> getAdditionalContent() {
          return List.of(new TextContent("see also: FAQ", null));
        }
      };
    }
  }

  static class UserNotFoundException extends McpToolException {
    private final String username;

    UserNotFoundException(String username) {
      super("User not found: " + username);
      this.username = username;
    }

    @Override
    public Object getStructuredContent() {
      return new UserNotFoundDetails("USER_NOT_FOUND", username);
    }
  }

  record UserNotFoundDetails(String code, String username) {}
}
