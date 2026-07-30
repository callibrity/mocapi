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
package com.callibrity.mocapi.conformance;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.AudioContent;
import com.callibrity.mocapi.model.BooleanSchema;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.EmbeddedResource;
import com.callibrity.mocapi.model.EnumItemsSchema;
import com.callibrity.mocapi.model.EnumOption;
import com.callibrity.mocapi.model.ImageContent;
import com.callibrity.mocapi.model.LegacyTitledEnumSchema;
import com.callibrity.mocapi.model.NumberSchema;
import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.StringSchema;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.model.TitledEnumItemsSchema;
import com.callibrity.mocapi.model.TitledMultiSelectEnumSchema;
import com.callibrity.mocapi.model.TitledSingleSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledMultiSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledSingleSelectEnumSchema;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Tools required by the {@code @modelcontextprotocol/conformance} npx suite. Each tool method
 * satisfies a specific conformance scenario. Tool names follow the {@code test_*} convention
 * expected by the suite.
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
 *     Specification</a>
 */
@Component
// 2026-07-28 suite (final). Logging and sampling left the protocol/API in the clean break
// (ctx.logger / ctx.sample removed, SEP-2577), so no tool exercises them; test_logging_tool below
// asserts the "no logs without logLevel" behaviour by construction. Two suite scenarios stay
// baselined in conformance-expected-failures.yaml because mocapi has no public API to express them:
// test_missing_capability (require the sampling capability -> -32021) and json_schema_2020_12_tool
// (advertise an explicit rich 2020-12 inputSchema; mocapi generates tool schemas from Java types,
// ADR-0016).
public class ConformanceTools {

  private static final String VALUE_1 = "value1";
  private static final String VALUE_2 = "value2";
  private static final String VALUE_3 = "value3";
  private static final String OPTION_1 = "option1";
  private static final String OPTION_2 = "option2";
  private static final String OPTION_3 = "option3";
  public static final String USERNAME_PROP = "username";
  public static final String EMAIL_PROP = "email";

  private static CallToolResult elicitationResult(com.callibrity.mocapi.model.ElicitResult result) {
    return new CallToolResult(
        List.of(new TextContent("Elicitation completed: action=" + result.action().toJson(), null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }

  // 1x1 red pixel PNG
  private static final String TINY_PNG =
      Base64.getEncoder()
          .encodeToString(
              new byte[] {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00,
                0x00,
                0x00,
                0x0D,
                0x49,
                0x48,
                0x44,
                0x52,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x00,
                0x00,
                0x01,
                0x08,
                0x02,
                0x00,
                0x00,
                0x00,
                (byte) 0x90,
                0x77,
                0x53,
                (byte) 0xDE,
                0x00,
                0x00,
                0x00,
                0x0C,
                0x49,
                0x44,
                0x41,
                0x54,
                0x08,
                (byte) 0xD7,
                0x63,
                (byte) 0xF8,
                (byte) 0xCF,
                (byte) 0xC0,
                0x00,
                0x00,
                0x00,
                0x02,
                0x00,
                0x01,
                (byte) 0xE2,
                0x21,
                (byte) 0xBC,
                0x33,
                0x00,
                0x00,
                0x00,
                0x00,
                0x49,
                0x45,
                0x4E,
                0x44,
                (byte) 0xAE,
                0x42,
                0x60,
                (byte) 0x82
              });

  // Minimal WAV header (44 bytes) for a silent 1-sample 8-bit mono file
  private static final String TINY_WAV =
      Base64.getEncoder()
          .encodeToString(
              new byte[] {
                0x52,
                0x49,
                0x46,
                0x46,
                0x25,
                0x00,
                0x00,
                0x00,
                0x57,
                0x41,
                0x56,
                0x45,
                0x66,
                0x6D,
                0x74,
                0x20,
                0x10,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x01,
                0x00,
                0x44,
                (byte) 0xAC,
                0x00,
                0x00,
                0x44,
                (byte) 0xAC,
                0x00,
                0x00,
                0x01,
                0x00,
                0x08,
                0x00,
                0x64,
                0x61,
                0x74,
                0x61,
                0x01,
                0x00,
                0x00,
                0x00,
                (byte) 0x80
              });

  /**
   * Conformance tool for the {@code tools-call-simple-text} scenario. Returns a single {@link
   * TextContent} response.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @McpTool(
      name = "test_simple_text",
      description = "Returns simple text content for conformance testing")
  public CallToolResult simpleText() {
    return new CallToolResult(
        List.of(new TextContent("This is a simple text response for testing.", null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code tools-call-image} scenario. Returns a single {@link
   * ImageContent} containing a 1x1 red pixel PNG.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @McpTool(
      name = "test_image_content",
      description = "Returns image content for conformance testing")
  public CallToolResult imageContent() {
    return new CallToolResult(
        List.of(new ImageContent(TINY_PNG, "image/png", null)), null, null, ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code tools-call-audio} scenario. Returns a single {@link
   * AudioContent} containing a minimal silent WAV file.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @McpTool(
      name = "test_audio_content",
      description = "Returns audio content for conformance testing")
  public CallToolResult audioContent() {
    return new CallToolResult(
        List.of(new AudioContent(TINY_WAV, "audio/wav", null)), null, null, ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code tools-call-embedded-resource} scenario. Returns an {@link
   * EmbeddedResource} with plain text content.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @McpTool(
      name = "test_embedded_resource",
      description = "Returns embedded resource content for conformance testing")
  public CallToolResult embeddedResource() {
    return new CallToolResult(
        List.of(
            new EmbeddedResource(
                new TextResourceContents(
                    "test://embedded-resource",
                    "text/plain",
                    "This is an embedded resource content."),
                null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code tools-call-mixed-content} scenario. Returns a response
   * combining {@link TextContent}, {@link ImageContent}, and {@link EmbeddedResource}.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @McpTool(
      name = "test_multiple_content_types",
      description = "Returns multiple content types for conformance testing")
  public CallToolResult mixedContent() {
    return new CallToolResult(
        List.of(
            new TextContent("Multiple content types test:", null),
            new ImageContent(TINY_PNG, "image/png", null),
            new EmbeddedResource(
                new TextResourceContents(
                    "test://mixed-content-resource",
                    "application/json",
                    "{\"test\":\"data\",\"value\":123}"),
                null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code tools-call-error} scenario. Returns a response with {@code
   * isError=true}.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @McpTool(
      name = "test_error_handling",
      description = "Always returns an error for conformance testing")
  public CallToolResult errorHandling() {
    return new CallToolResult(
        List.of(new TextContent("This tool intentionally returns an error for testing", null)),
        true,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code tools-call-with-progress} scenario. Sends three {@code
   * notifications/progress} updates (0/100, 50/100, 100/100) during execution.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @McpTool(
      name = "test_tool_with_progress",
      description = "Reports progress notifications for conformance testing")
  public CallToolResult withProgress(McpToolContext ctx) throws InterruptedException {
    var progress = ctx.longProgress(100L);
    progress.emit(0);
    Thread.sleep(50);
    progress.emit(50);
    Thread.sleep(50);
    progress.emit(100);
    return new CallToolResult(
        List.of(new TextContent("Progress test completed successfully", null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code tools-call-elicitation} scenario. Issues an {@code
   * elicitation/create} request to the client asking for username and email.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation">MCP
   *     Elicitation Specification</a>
   */
  @McpTool(name = "test_elicitation", description = "Tests elicitation/create for conformance")
  public CallToolResult testElicitation(String message, McpToolContext ctx) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put(
        USERNAME_PROP, new StringSchema("User's response", null, null, null, null, null));
    properties.put(
        EMAIL_PROP, new StringSchema("User's email address", null, null, null, null, null));
    var schema = new RequestedSchema(properties, List.of(USERNAME_PROP, EMAIL_PROP), null);
    var params = new ElicitRequestFormParams(message, schema);
    var result = ctx.elicit(params);
    String content = result.isAccepted() ? result.getString(USERNAME_PROP) : "n/a";
    return new CallToolResult(
        List.of(
            new TextContent(
                "User response: action=" + result.action().toJson() + ", content=" + content,
                null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code elicitation-sep1034-defaults} scenario. Exercises default
   * values across all primitive schema types (string, integer, number, enum, boolean).
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation">MCP
   *     Elicitation Specification</a>
   */
  @McpTool(
      name = "test_elicitation_sep1034_defaults",
      description = "Tests elicitation with default values for conformance")
  public CallToolResult testElicitationDefaults(McpToolContext ctx) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put("name", new StringSchema("Name", null, null, null, null, "John Doe"));
    properties.put("age", new NumberSchema("integer", "Age", null, null, null, 30));
    properties.put("score", new NumberSchema("number", "Score", null, null, null, 95.5));
    properties.put(
        "status",
        new UntitledSingleSelectEnumSchema(
            null, null, List.of("active", "inactive", "pending"), "active"));
    properties.put("verified", new BooleanSchema("Verified", null, true));
    var schema = new RequestedSchema(properties, List.of(), null);
    var params = new ElicitRequestFormParams("Enter defaults test data", schema);
    var result = ctx.elicit(params);
    return elicitationResult(result);
  }

  /**
   * Conformance tool for the {@code elicitation-sep1330-enums} scenario. Exercises all five enum
   * variants: untitled single, titled single, legacy enum, untitled multi, and titled multi.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation">MCP
   *     Elicitation Specification</a>
   */
  @SuppressWarnings(
      "deprecation") // Conformance test exercises deprecated LegacyTitledEnumSchema per MCP spec
  // backward compatibility
  @McpTool(
      name = "test_elicitation_sep1330_enums",
      description = "Tests elicitation with enum variants for conformance")
  public CallToolResult testElicitationEnums(McpToolContext ctx) {
    var properties = new LinkedHashMap<String, PrimitiveSchemaDefinition>();
    properties.put(
        "untitledSingle",
        new UntitledSingleSelectEnumSchema(
            null, null, List.of(OPTION_1, OPTION_2, OPTION_3), null));
    properties.put(
        "titledSingle",
        new TitledSingleSelectEnumSchema(
            null,
            null,
            List.of(
                new EnumOption(VALUE_1, "First Option"),
                new EnumOption(VALUE_2, "Second Option"),
                new EnumOption(VALUE_3, "Third Option")),
            null));
    properties.put(
        "legacyEnum",
        new LegacyTitledEnumSchema(
            null,
            null,
            List.of("opt1", "opt2", "opt3"),
            List.of("Option One", "Option Two", "Option Three"),
            null));
    properties.put(
        "untitledMulti",
        new UntitledMultiSelectEnumSchema(
            null,
            null,
            null,
            null,
            new EnumItemsSchema(List.of(OPTION_1, OPTION_2, OPTION_3)),
            null));
    properties.put(
        "titledMulti",
        new TitledMultiSelectEnumSchema(
            null,
            null,
            null,
            null,
            new TitledEnumItemsSchema(
                List.of(
                    new EnumOption(VALUE_1, "First Choice"),
                    new EnumOption(VALUE_2, "Second Choice"),
                    new EnumOption(VALUE_3, "Third Choice"))),
            null));
    var schema = new RequestedSchema(properties, List.of(), null);
    var params = new ElicitRequestFormParams("Enum variants test", schema);
    var result = ctx.elicit(params);
    return elicitationResult(result);
  }

  /**
   * Conformance tool for the {@code server-stateless} suite's "no logs without logLevel" check.
   * mocapi does not implement MCP logging (SEP-2577 deprecated; {@code ctx.logger} was removed in
   * the clean break), so it never emits a log notification — trivially satisfying the requirement.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2026-07-28/server">MCP
   *     2026-07-28</a>
   */
  @McpTool(
      name = "test_logging_tool",
      description = "Diagnostic logging validator; mocapi emits no log notifications")
  public CallToolResult loggingTool() {
    return new CallToolResult(
        List.of(new TextContent("No log notifications are emitted.", null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }

  /**
   * Conformance tool for the {@code server-stateless} suite's response-stream check: the stream
   * must contain only result/notification chunks and never an independent server-initiated JSON-RPC
   * request. mocapi is stateless and never issues server-initiated requests (ADR-0021); this tool
   * emits a progress notification to exercise the stream, then completes.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2026-07-28/server">MCP
   *     2026-07-28</a>
   */
  @McpTool(
      name = "test_streaming_elicitation",
      description = "Diagnostic response-stream validator; emits a progress notification")
  public CallToolResult streamingElicitation(McpToolContext ctx) {
    ctx.longProgress(100L).emit(50);
    return new CallToolResult(
        List.of(new TextContent("Response stream completed.", null)),
        null,
        null,
        ResultTypes.COMPLETE);
  }
}
