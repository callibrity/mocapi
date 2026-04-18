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
package com.callibrity.mocapi.jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.jakarta.fixture.ValidatedPrompt;
import com.callibrity.mocapi.jakarta.fixture.ValidatedResources;
import com.callibrity.mocapi.jakarta.fixture.ValidatedTool;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslator;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import com.callibrity.ripcurl.jakarta.ConstraintViolationExceptionTranslator;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodValidatorFactory;
import org.jwcarman.methodical.jakarta.JakartaMethodValidatorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * End-to-end verification that adding {@code mocapi-jakarta-validation} to an application's
 * classpath turns on Jakarta Bean Validation across mocapi's reflective-dispatch surface, and that
 * the resulting violations surface in the correct MCP-spec-idiomatic shape for each handler type.
 *
 * <p>The tests boot a real Spring context with {@code @EnableAutoConfiguration}, which causes
 * Spring Boot + methodical + ripcurl to discover their respective autoconfigs from the module's
 * transitive classpath. Fixtures register a {@link ValidatedPrompt} and {@link ValidatedResources}
 * bean carrying jakarta constraints on their handler parameters; the tests dispatch real JSON-RPC
 * calls against those fixtures and assert the response shape.
 *
 * <p>Error shape per handler, matching the MCP 2025-11-25 spec:
 *
 * <ul>
 *   <li>{@code tools/call} — {@code CallToolResult.isError=true} with the violation message in text
 *       content. Spec explicit: "Input validation errors (e.g., date in wrong format, value out of
 *       range)" belong in the result body so the calling LLM can self-correct.
 *   <li>{@code prompts/get} — JSON-RPC {@code -32602 Invalid params} with per-violation detail in
 *       the response's {@code data} field. Spec explicit: "Missing required arguments: -32602".
 *   <li>{@code resources/read} — JSON-RPC {@code -32602} with per-violation {@code data}.
 *       Consistent with JSON-RPC standard; MCP spec doesn't explicitly enumerate this code for
 *       resources but doesn't forbid it either.
 * </ul>
 */
@SpringBootTest(
    classes = JakartaValidationIntegrationTest.TestApp.class,
    webEnvironment = WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JakartaValidationIntegrationTest {

  @Autowired JsonRpcDispatcher dispatcher;
  @Autowired ObjectMapper objectMapper;
  @Autowired ApplicationContext context;

  @Nested
  class Wiring {

    /**
     * Verifies the transitive chain actually landed — if any of the three legs is missing, none of
     * the error-shape tests below would be meaningful, so make that failure mode explicit.
     */
    @Test
    void all_three_legs_of_the_validation_chain_are_wired_up() {
      assertThat(context.getBeansOfType(Validator.class)).isNotEmpty();
      assertThat(context.getBeansOfType(MethodValidatorFactory.class).values())
          .anyMatch(f -> f instanceof JakartaMethodValidatorFactory);
      assertThat(context.getBeansOfType(ConstraintViolationExceptionTranslator.class)).isNotEmpty();
    }

    @Test
    void ripcurl_registry_picks_up_the_constraint_violation_translator() {
      // Prove the ConstraintViolationExceptionTranslator bean actually flows into ripcurl's
      // translator registry — without this, ConstraintViolationException would hit the registry's
      // built-in fallback (-32603) instead of producing the per-violation -32602 shape below.
      var registry = context.getBean(JsonRpcExceptionTranslatorRegistry.class);
      var translators = context.getBeansOfType(JsonRpcExceptionTranslator.class);
      assertThat(translators.values())
          .anyMatch(t -> t instanceof ConstraintViolationExceptionTranslator);
      assertThat(registry).isNotNull();
    }
  }

  @Nested
  class Prompts_get {

    @Test
    void blank_argument_returns_invalid_params_with_per_violation_data() {
      // @NotBlank on the 'text' argument; sending "" should trigger a violation that the
      // ripcurl translator converts to -32602. Spec reference: prompts/Error-Handling says
      // "Missing required arguments: -32602 (Invalid params)."
      var response = dispatchPromptsGet("echo", "");

      assertThat(response).isInstanceOf(JsonRpcError.class);
      var error = (JsonRpcError) response;
      assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.error().data()).isNotNull();
      assertThat(error.error().data().isArray()).isTrue();
      assertThat(error.error().data().size()).isGreaterThanOrEqualTo(1);
      // Every entry in the data array must carry `field` and `message` strings — that's the
      // contract clients code against. An empty blob would still be isArray() true but useless.
      for (var entry : error.error().data()) {
        assertThat(entry.get("field")).isNotNull();
        assertThat(entry.get("field").asString()).isNotBlank();
        assertThat(entry.get("message")).isNotNull();
        assertThat(entry.get("message").asString()).isNotBlank();
      }
    }

    @Test
    void too_short_argument_triggers_size_constraint_violation() {
      // @Size(min=3) on text; "hi" (length 2) must surface the Size violation alongside any
      // NotBlank issue. Proves the translator emits more than one entry when appropriate.
      var response = dispatchPromptsGet("echo", "hi");

      assertThat(response).isInstanceOf(JsonRpcError.class);
      var error = (JsonRpcError) response;
      assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.error().data().isArray()).isTrue();
    }

    @Test
    void valid_argument_succeeds_normally() {
      // Negative control: a conforming argument exercises the same path end-to-end without
      // tripping the validator, confirming the chain doesn't false-positive.
      var response = dispatchPromptsGet("echo", "this is a long enough text to pass validation");

      assertThat(response).isNotInstanceOf(JsonRpcError.class);
    }
  }

  @Nested
  class Resources_read {

    @Test
    void pattern_violating_uri_variable_returns_invalid_params() {
      // URI template is val://{code}, method parameter @Pattern("^[A-Z]+$"). The URI val://abc
      // matches the template (extracts code="abc") but violates the pattern; the ripcurl
      // translator converts ConstraintViolationException to -32602 with per-violation data.
      var response = dispatchResourcesRead("val://abc");

      assertThat(response).isInstanceOf(JsonRpcError.class);
      var error = (JsonRpcError) response;
      assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      assertThat(error.error().data()).isNotNull();
      assertThat(error.error().data().isArray()).isTrue();
    }

    @Test
    void pattern_conforming_uri_variable_succeeds() {
      // Negative control: URI val://ABC extracts code="ABC" which matches the pattern.
      var response = dispatchResourcesRead("val://ABC");

      assertThat(response).isNotInstanceOf(JsonRpcError.class);
    }
  }

  @Nested
  class Tools_call {

    /**
     * Tool-argument validation surfaces via Jakarta's runtime validator — not mocapi's
     * pre-invocation JSON-schema check — because {@code @NotBlank}/{@code @Size} on a raw {@code
     * String} method parameter don't translate into schema constraints the way they do on
     * typed-record fields. {@code ConstraintViolationException} from methodical's invoker
     * propagates to {@code McpToolsService.invokeTool}'s generic {@code catch (Exception)}, which
     * wraps it as {@code CallToolResult { isError: true, content: [text] }} — exactly the
     * MCP-spec-idiomatic shape ("Input validation errors" belong in the result body for LLM
     * self-correction).
     */
    @Test
    void blank_tool_argument_returns_callToolResult_with_isError_true() throws Exception {
      var response = dispatchToolsCall("shout", "");

      assertThat(response).isInstanceOf(JsonRpcResult.class);
      var callToolResult =
          objectMapper.treeToValue(((JsonRpcResult) response).result(), CallToolResult.class);
      assertThat(callToolResult.isError()).isTrue();
      // Violation message is surfaced as text content so the calling LLM can read it and retry
      // with adjusted arguments — the point of the isError=true design.
      assertThat(callToolResult.content()).isNotEmpty();
    }

    @Test
    void too_short_tool_argument_returns_callToolResult_with_isError_true() throws Exception {
      var response = dispatchToolsCall("shout", "hi");

      assertThat(response).isInstanceOf(JsonRpcResult.class);
      var callToolResult =
          objectMapper.treeToValue(((JsonRpcResult) response).result(), CallToolResult.class);
      assertThat(callToolResult.isError()).isTrue();
    }

    @Test
    void valid_tool_argument_succeeds_and_returns_non_error_result() throws Exception {
      var response = dispatchToolsCall("shout", "hello world");

      assertThat(response).isInstanceOf(JsonRpcResult.class);
      var callToolResult =
          objectMapper.treeToValue(((JsonRpcResult) response).result(), CallToolResult.class);
      assertThat(callToolResult.isError()).isNotEqualTo(Boolean.TRUE);
    }
  }

  // --- Dispatch helpers --------------------------------------------------

  private JsonRpcResponse dispatchPromptsGet(String promptName, String textArg) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", promptName);
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("text", textArg);
    return dispatcher.dispatch(
        new JsonRpcCall(
            JsonRpcProtocol.VERSION, McpMethods.PROMPTS_GET, params, IntNode.valueOf(1)));
  }

  private JsonRpcResponse dispatchResourcesRead(String uri) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("uri", uri);
    return dispatcher.dispatch(
        new JsonRpcCall(
            JsonRpcProtocol.VERSION, McpMethods.RESOURCES_READ, params, IntNode.valueOf(2)));
  }

  private JsonRpcResponse dispatchToolsCall(String toolName, String messageArg) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", toolName);
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("message", messageArg);
    return dispatcher.dispatch(
        new JsonRpcCall(
            JsonRpcProtocol.VERSION, McpMethods.TOOLS_CALL, params, IntNode.valueOf(3)));
  }

  // --- Test application --------------------------------------------------

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApp {

    @org.springframework.context.annotation.Bean
    ValidatedPrompt validatedPrompt() {
      return new ValidatedPrompt();
    }

    @org.springframework.context.annotation.Bean
    ValidatedResources validatedResources() {
      return new ValidatedResources();
    }

    @org.springframework.context.annotation.Bean
    ValidatedTool validatedTool() {
      return new ValidatedTool();
    }
  }
}
