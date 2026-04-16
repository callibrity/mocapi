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
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

class MocapiRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  {
    new MocapiRuntimeHints().registerHints(hints, getClass().getClassLoader());
  }

  @Test
  void registersBindingHintsForMcpSession() {
    assertTypeHintRegistered(McpSession.class);
  }

  @Test
  void registersHintsForJsonRpcEnvelopeTypes() {
    assertTypeHintRegistered(CallToolResult.class);
    assertTypeHintRegistered(GetPromptResult.class);
    assertTypeHintRegistered(ReadResourceResult.class);
    assertTypeHintRegistered(ListToolsResult.class);
  }

  @Test
  void registersHintsForDescriptorTypes() {
    assertTypeHintRegistered(Tool.class);
    assertTypeHintRegistered(Prompt.class);
    assertTypeHintRegistered(Resource.class);
    assertTypeHintRegistered(ServerCapabilities.class);
  }

  @Test
  void registersHintsForSealedContentBlockHierarchy() {
    assertTypeHintRegistered(ContentBlock.class);
    assertTypeHintRegistered(TextContent.class);
  }

  @Test
  void registersHintsForSealedResourceContentsHierarchy() {
    assertTypeHintRegistered(ResourceContents.class);
    assertTypeHintRegistered(TextResourceContents.class);
    assertTypeHintRegistered(BlobResourceContents.class);
  }

  @Test
  void registersHintsForNestedPromptMessage() {
    assertTypeHintRegistered(PromptMessage.class);
  }

  private void assertTypeHintRegistered(Class<?> type) {
    assertThat(hints.reflection().typeHints())
        .as("expected binding hints for %s", type.getName())
        .anyMatch(th -> th.getType().equals(TypeReference.of(type)));
  }
}
