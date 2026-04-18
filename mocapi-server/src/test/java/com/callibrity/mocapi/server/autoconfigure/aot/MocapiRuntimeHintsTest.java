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
package com.callibrity.mocapi.server.autoconfigure.aot;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.BlobResourceContents;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.ListToolsResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceContents;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.session.McpSession;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  {
    new MocapiRuntimeHints().registerHints(hints, getClass().getClassLoader());
  }

  @Test
  void registers_binding_hints_for_mcp_session() {
    assertTypeHintRegistered(McpSession.class);
  }

  @Test
  void registers_hints_for_json_rpc_envelope_types() {
    assertTypeHintRegistered(CallToolResult.class);
    assertTypeHintRegistered(GetPromptResult.class);
    assertTypeHintRegistered(ReadResourceResult.class);
    assertTypeHintRegistered(ListToolsResult.class);
  }

  @Test
  void registers_hints_for_descriptor_types() {
    assertTypeHintRegistered(Tool.class);
    assertTypeHintRegistered(Prompt.class);
    assertTypeHintRegistered(Resource.class);
    assertTypeHintRegistered(ServerCapabilities.class);
  }

  @Test
  void registers_hints_for_sealed_content_block_hierarchy() {
    assertTypeHintRegistered(ContentBlock.class);
    assertTypeHintRegistered(TextContent.class);
  }

  @Test
  void registers_hints_for_sealed_resource_contents_hierarchy() {
    assertTypeHintRegistered(ResourceContents.class);
    assertTypeHintRegistered(TextResourceContents.class);
    assertTypeHintRegistered(BlobResourceContents.class);
  }

  @Test
  void registers_hints_for_nested_prompt_message() {
    assertTypeHintRegistered(PromptMessage.class);
  }

  @Test
  void registers_resource_patterns_for_json_skema_meta_schemas() {
    var patterns =
        hints
            .resources()
            .resourcePatternHints()
            .flatMap(hint -> hint.getIncludes().stream())
            .map(p -> p.getPattern())
            .toList();
    assertThat(patterns).contains("json-meta-schemas/*", "json-meta-schemas/draft2020-12/*");
  }

  private void assertTypeHintRegistered(Class<?> type) {
    assertThat(hints.reflection().typeHints())
        .as("expected binding hints for %s", type.getName())
        .anyMatch(th -> th.getType().equals(TypeReference.of(type)));
  }
}
