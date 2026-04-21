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
package com.callibrity.mocapi.server.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.tools.McpTool;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HandlerKindTest {

  @Test
  void of_returns_tool_for_method_annotated_with_mcp_tool() throws Exception {
    assertThat(HandlerKind.of(method("toolMethod"))).isEqualTo(HandlerKind.TOOL);
  }

  @Test
  void of_returns_prompt_for_method_annotated_with_mcp_prompt() throws Exception {
    assertThat(HandlerKind.of(method("promptMethod"))).isEqualTo(HandlerKind.PROMPT);
  }

  @Test
  void of_returns_resource_for_method_annotated_with_mcp_resource() throws Exception {
    assertThat(HandlerKind.of(method("resourceMethod"))).isEqualTo(HandlerKind.RESOURCE);
  }

  @Test
  void of_returns_resource_template_for_method_annotated_with_mcp_resource_template()
      throws Exception {
    assertThat(HandlerKind.of(method("resourceTemplateMethod")))
        .isEqualTo(HandlerKind.RESOURCE_TEMPLATE);
  }

  @Test
  void of_returns_null_for_method_with_no_mcp_annotation() throws Exception {
    assertThat(HandlerKind.of(method("unannotatedMethod"))).isNull();
  }

  @Test
  void name_of_returns_tool_annotation_name() throws Exception {
    assertThat(HandlerKind.TOOL.nameOf(method("toolMethod"))).isEqualTo("my-tool");
  }

  @Test
  void name_of_returns_prompt_annotation_name() throws Exception {
    assertThat(HandlerKind.PROMPT.nameOf(method("promptMethod"))).isEqualTo("my-prompt");
  }

  @Test
  void name_of_returns_resource_annotation_uri() throws Exception {
    assertThat(HandlerKind.RESOURCE.nameOf(method("resourceMethod"))).isEqualTo("mem://hello");
  }

  @Test
  void name_of_returns_resource_template_annotation_uri_template() throws Exception {
    assertThat(HandlerKind.RESOURCE_TEMPLATE.nameOf(method("resourceTemplateMethod")))
        .isEqualTo("mem://item/{id}");
  }

  @Test
  void tag_returns_lowercase_stable_form_for_each_kind() {
    assertThat(HandlerKind.TOOL.tag()).isEqualTo("tool");
    assertThat(HandlerKind.PROMPT.tag()).isEqualTo("prompt");
    assertThat(HandlerKind.RESOURCE.tag()).isEqualTo("resource");
    assertThat(HandlerKind.RESOURCE_TEMPLATE.tag()).isEqualTo("resource_template");
  }

  private static Method method(String name) throws NoSuchMethodException {
    return Fixtures.class.getDeclaredMethod(name);
  }

  static class Fixtures {
    @McpTool(name = "my-tool")
    public void toolMethod() {
      /* annotation target only */
    }

    @McpPrompt(name = "my-prompt")
    public void promptMethod() {
      /* annotation target only */
    }

    @McpResource(uri = "mem://hello")
    public void resourceMethod() {
      /* annotation target only */
    }

    @McpResourceTemplate(uriTemplate = "mem://item/{id}")
    public void resourceTemplateMethod() {
      /* annotation target only */
    }

    public void unannotatedMethod() {
      /* annotation target only */
    }
  }
}
