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
package com.callibrity.mocapi.example.tools;

import com.callibrity.mocapi.stream.ElicitationResult;
import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.mocapi.stream.SamplingResult;
import com.callibrity.mocapi.tools.ToolsRegistry.AudioContent;
import com.callibrity.mocapi.tools.ToolsRegistry.CallToolResponse;
import com.callibrity.mocapi.tools.ToolsRegistry.EmbeddedResource;
import com.callibrity.mocapi.tools.ToolsRegistry.ImageContent;
import com.callibrity.mocapi.tools.ToolsRegistry.ResourceContent;
import com.callibrity.mocapi.tools.ToolsRegistry.TextContent;
import com.callibrity.mocapi.tools.annotation.Tool;
import com.callibrity.mocapi.tools.annotation.ToolService;
import java.util.Base64;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Tools required by the MCP conformance test suite ({@code @modelcontextprotocol/conformance}). */
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

  @Tool(
      name = "test_simple_text",
      description = "Returns simple text content for conformance testing")
  public CallToolResponse simpleText() {
    return new CallToolResponse(
        List.of(new TextContent("This is a simple text response for testing.")), null, null);
  }

  @Tool(name = "test_image_content", description = "Returns image content for conformance testing")
  public CallToolResponse imageContent() {
    return new CallToolResponse(List.of(new ImageContent(TINY_PNG, "image/png")), null, null);
  }

  @Tool(name = "test_audio_content", description = "Returns audio content for conformance testing")
  public CallToolResponse audioContent() {
    return new CallToolResponse(List.of(new AudioContent(TINY_WAV, "audio/wav")), null, null);
  }

  @Tool(
      name = "test_embedded_resource",
      description = "Returns embedded resource content for conformance testing")
  public CallToolResponse embeddedResource() {
    return new CallToolResponse(
        List.of(
            new ResourceContent(
                new EmbeddedResource(
                    "test://embedded-resource",
                    "text/plain",
                    "This is an embedded resource content.",
                    null))),
        null,
        null);
  }

  @Tool(
      name = "test_multiple_content_types",
      description = "Returns multiple content types for conformance testing")
  public CallToolResponse mixedContent() {
    return new CallToolResponse(
        List.of(
            new TextContent("Multiple content types test:"),
            new ImageContent(TINY_PNG, "image/png"),
            new ResourceContent(
                new EmbeddedResource(
                    "test://mixed-content-resource",
                    "application/json",
                    "{\"test\":\"data\",\"value\":123}",
                    null))),
        null,
        null);
  }

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

  @Tool(
      name = "test_error_handling",
      description = "Always returns an error for conformance testing")
  public CallToolResponse errorHandling() {
    return new CallToolResponse(
        List.of(new TextContent("This tool intentionally returns an error for testing")),
        true,
        null);
  }

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

  @Tool(name = "test_sampling", description = "Tests sampling/createMessage for conformance")
  public CallToolResponse testSampling(String prompt, McpStreamContext<CallToolResponse> ctx) {
    SamplingResult result = ctx.sample(prompt, 100);
    String text = result.text();
    return new CallToolResponse(List.of(new TextContent("LLM response: " + text)), null, null);
  }

  @Tool(name = "test_elicitation", description = "Tests elicitation/create for conformance")
  public CallToolResponse testElicitation(String message, McpStreamContext<CallToolResponse> ctx) {
    ElicitationResult<JsonNode> result =
        ctx.elicit(
            message,
            schema ->
                schema
                    .string("username", "User's response")
                    .string("email", "User's email address")
                    .required("username", "email"));
    return new CallToolResponse(
        List.of(
            new TextContent(
                "User response: action="
                    + result.action().getValue()
                    + ", content="
                    + result.content())),
        null,
        null);
  }

  enum DefaultsStatus {
    ACTIVE,
    INACTIVE,
    PENDING
  }

  @Tool(
      name = "test_elicitation_sep1034_defaults",
      description = "Tests elicitation with default values for conformance")
  public CallToolResponse testElicitationDefaults(McpStreamContext<CallToolResponse> ctx) {
    ElicitationResult<JsonNode> result =
        ctx.elicit(
            "Enter defaults test data",
            schema ->
                schema
                    .string("name", "Name", "John Doe")
                    .integer("age", "Age", 30)
                    .number("score", "Score", 95.5)
                    .choose("status", DefaultsStatus.class, DefaultsStatus.ACTIVE)
                    .bool("verified", "Verified", true));
    return new CallToolResponse(
        List.of(
            new TextContent(
                "Elicitation completed: action="
                    + result.action().getValue()
                    + ", content="
                    + result.content())),
        null,
        null);
  }

  enum UntitledOption {
    OPTION1,
    OPTION2,
    OPTION3
  }

  enum TitledOption {
    VALUE1("First Option"),
    VALUE2("Second Option"),
    VALUE3("Third Option");

    private final String title;

    TitledOption(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  @Tool(
      name = "test_elicitation_sep1330_enums",
      description = "Tests elicitation with enum variants for conformance")
  public CallToolResponse testElicitationEnums(McpStreamContext<CallToolResponse> ctx) {
    ElicitationResult<JsonNode> result =
        ctx.elicit(
            "Enum variants test",
            schema ->
                schema
                    .choose("untitled_single", UntitledOption.class)
                    .choose("titled_single", TitledOption.class)
                    .chooseMany("untitled_multi", UntitledOption.class)
                    .chooseMany("titled_multi", TitledOption.class));
    return new CallToolResponse(
        List.of(
            new TextContent(
                "Elicitation completed: action="
                    + result.action().getValue()
                    + ", content="
                    + result.content())),
        null,
        null);
  }
}
