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
package com.callibrity.mocapi.compat.conformance;

import com.callibrity.mocapi.content.AudioContent;
import com.callibrity.mocapi.content.CallToolResponse;
import com.callibrity.mocapi.content.EmbeddedResource;
import com.callibrity.mocapi.content.ImageContent;
import com.callibrity.mocapi.content.TextContent;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.mocapi.stream.SamplingResult;
import com.callibrity.mocapi.tools.annotation.Tool;
import com.callibrity.mocapi.tools.annotation.ToolService;
import java.util.Base64;
import java.util.List;

/**
 * Tools required by the {@code @modelcontextprotocol/conformance} npx suite. Each tool method
 * satisfies a specific conformance scenario. Tool names follow the {@code test_*} convention
 * expected by the suite.
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
 *     Specification</a>
 */
@ToolService
public class ConformanceTools {

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
  @Tool(
      name = "test_simple_text",
      description = "Returns simple text content for conformance testing")
  public CallToolResponse simpleText() {
    return new CallToolResponse(
        List.of(new TextContent("This is a simple text response for testing.")), null, null);
  }

  /**
   * Conformance tool for the {@code tools-call-image} scenario. Returns a single {@link
   * ImageContent} containing a 1x1 red pixel PNG.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @Tool(name = "test_image_content", description = "Returns image content for conformance testing")
  public CallToolResponse imageContent() {
    return new CallToolResponse(List.of(new ImageContent(TINY_PNG, "image/png")), null, null);
  }

  /**
   * Conformance tool for the {@code tools-call-audio} scenario. Returns a single {@link
   * AudioContent} containing a minimal silent WAV file.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @Tool(name = "test_audio_content", description = "Returns audio content for conformance testing")
  public CallToolResponse audioContent() {
    return new CallToolResponse(List.of(new AudioContent(TINY_WAV, "audio/wav")), null, null);
  }

  /**
   * Conformance tool for the {@code tools-call-embedded-resource} scenario. Returns an {@link
   * EmbeddedResource} with plain text content.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @Tool(
      name = "test_embedded_resource",
      description = "Returns embedded resource content for conformance testing")
  public CallToolResponse embeddedResource() {
    return new CallToolResponse(
        List.of(
            new EmbeddedResource(
                new TextResourceContents(
                    "test://embedded-resource",
                    "text/plain",
                    "This is an embedded resource content."))),
        null,
        null);
  }

  /**
   * Conformance tool for the {@code tools-call-mixed-content} scenario. Returns a response
   * combining {@link TextContent}, {@link ImageContent}, and {@link EmbeddedResource}.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @Tool(
      name = "test_multiple_content_types",
      description = "Returns multiple content types for conformance testing")
  public CallToolResponse mixedContent() {
    return new CallToolResponse(
        List.of(
            new TextContent("Multiple content types test:"),
            new ImageContent(TINY_PNG, "image/png"),
            new EmbeddedResource(
                new TextResourceContents(
                    "test://mixed-content-resource",
                    "application/json",
                    "{\"test\":\"data\",\"value\":123}"))),
        null,
        null);
  }

  /**
   * Conformance tool for the {@code tools-call-with-logging} scenario. Sends three {@code
   * notifications/message} log entries during execution.
   *
   * @see <a
   *     href="https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/logging">MCP
   *     Logging Specification</a>
   */
  @Tool(
      name = "test_tool_with_logging",
      description = "Sends log messages during execution for conformance testing")
  public CallToolResponse withLogging(McpStreamContext<CallToolResponse> ctx)
      throws InterruptedException {
    ctx.log(
        com.callibrity.mocapi.session.LogLevel.INFO,
        "test_tool_with_logging",
        "Tool execution started");
    Thread.sleep(50);
    ctx.log(
        com.callibrity.mocapi.session.LogLevel.INFO,
        "test_tool_with_logging",
        "Tool processing data");
    Thread.sleep(50);
    ctx.log(
        com.callibrity.mocapi.session.LogLevel.INFO,
        "test_tool_with_logging",
        "Tool execution completed");
    return new CallToolResponse(
        List.of(new TextContent("Logging test completed successfully")), null, null);
  }

  /**
   * Conformance tool for the {@code tools-call-error} scenario. Returns a response with {@code
   * isError=true}.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @Tool(
      name = "test_error_handling",
      description = "Always returns an error for conformance testing")
  public CallToolResponse errorHandling() {
    return new CallToolResponse(
        List.of(new TextContent("This tool intentionally returns an error for testing")),
        true,
        null);
  }

  /**
   * Conformance tool for the {@code tools-call-with-progress} scenario. Sends three {@code
   * notifications/progress} updates (0/100, 50/100, 100/100) during execution.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools
   *     Specification</a>
   */
  @Tool(
      name = "test_tool_with_progress",
      description = "Reports progress notifications for conformance testing")
  public CallToolResponse withProgress(McpStreamContext<CallToolResponse> ctx)
      throws InterruptedException {
    ctx.sendProgress(0, 100);
    Thread.sleep(50);
    ctx.sendProgress(50, 100);
    Thread.sleep(50);
    ctx.sendProgress(100, 100);
    return new CallToolResponse(
        List.of(new TextContent("Progress test completed successfully")), null, null);
  }

  /**
   * Conformance tool for the {@code tools-call-sampling} scenario. Issues a {@code
   * sampling/createMessage} request to the client and returns the LLM response.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/client/sampling">MCP
   *     Sampling Specification</a>
   */
  @Tool(name = "test_sampling", description = "Tests sampling/createMessage for conformance")
  public CallToolResponse testSampling(String prompt, McpStreamContext<CallToolResponse> ctx) {
    SamplingResult result = ctx.sample(prompt, 100);
    String text = result.text();
    return new CallToolResponse(List.of(new TextContent("LLM response: " + text)), null, null);
  }

  /**
   * Conformance tool for the {@code tools-call-elicitation} scenario. Issues an {@code
   * elicitation/create} request to the client asking for username and email.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation">MCP
   *     Elicitation Specification</a>
   */
  @Tool(name = "test_elicitation", description = "Tests elicitation/create for conformance")
  public CallToolResponse testElicitation(String message, McpStreamContext<CallToolResponse> ctx) {
    var result =
        ctx.elicit(
            message,
            schema -> {
              schema.string("username", "User's response");
              schema.string("email", "User's email address");
            });
    String content = result.isAccepted() ? result.getString("username") : "n/a";
    return new CallToolResponse(
        List.of(
            new TextContent(
                "User response: action=" + result.action().getValue() + ", content=" + content)),
        null,
        null);
  }

  /**
   * Conformance tool for the {@code elicitation-sep1034-defaults} scenario. Exercises default
   * values across all primitive schema types (string, integer, number, enum, boolean).
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation">MCP
   *     Elicitation Specification</a>
   */
  @Tool(
      name = "test_elicitation_sep1034_defaults",
      description = "Tests elicitation with default values for conformance")
  public CallToolResponse testElicitationDefaults(McpStreamContext<CallToolResponse> ctx) {
    var result =
        ctx.elicit(
            "Enter defaults test data",
            schema -> {
              schema.string("name", "Name", "John Doe");
              schema.integer("age", "Age", 30);
              schema.number("score", "Score", 95.5);
              schema.choose("status", List.of("active", "inactive", "pending"), "active");
              schema.bool("verified", "Verified", true);
            });
    return new CallToolResponse(
        List.of(new TextContent("Elicitation completed: action=" + result.action().getValue())),
        null,
        null);
  }

  enum TitledOption {
    value1("First Option"),
    value2("Second Option"),
    value3("Third Option");

    private final String title;

    TitledOption(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  enum TitledMultiOption {
    value1("First Choice"),
    value2("Second Choice"),
    value3("Third Choice");

    private final String title;

    TitledMultiOption(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  /**
   * Conformance tool for the {@code elicitation-sep1330-enums} scenario. Exercises all five enum
   * variants: untitled single, titled single, legacy enum, untitled multi, and titled multi.
   *
   * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation">MCP
   *     Elicitation Specification</a>
   */
  @Tool(
      name = "test_elicitation_sep1330_enums",
      description = "Tests elicitation with enum variants for conformance")
  public CallToolResponse testElicitationEnums(McpStreamContext<CallToolResponse> ctx) {
    var result =
        ctx.elicit(
            "Enum variants test",
            schema -> {
              schema.choose("untitledSingle", List.of("option1", "option2", "option3"));
              schema.choose("titledSingle", TitledOption.class);
              schema.chooseLegacy(
                  "legacyEnum",
                  List.of("opt1", "opt2", "opt3"),
                  List.of("Option One", "Option Two", "Option Three"));
              schema.chooseMany("untitledMulti", List.of("option1", "option2", "option3"));
              schema.chooseMany("titledMulti", TitledMultiOption.class);
            });
    return new CallToolResponse(
        List.of(new TextContent("Elicitation completed: action=" + result.action().getValue())),
        null,
        null);
  }
}
