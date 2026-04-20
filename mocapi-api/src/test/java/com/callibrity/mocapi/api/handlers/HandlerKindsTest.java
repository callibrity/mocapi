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
package com.callibrity.mocapi.api.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.tools.McpTool;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HandlerKindsTest {

  // Reflection targets used by the tests below. They're invoked via
  // Class.getDeclaredMethod("<name>") rather than called directly, so Sonar's unused-private
  // and empty-method detectors don't see the usages; suppress both categories here.
  @SuppressWarnings({"java:S1186", "java:S1144"})
  @McpTool(name = "my-tool")
  private void toolMethod() {
    /* reflection target — see class comment */
  }

  @SuppressWarnings({"java:S1186", "java:S1144"})
  @McpPrompt(name = "my-prompt")
  private void promptMethod() {
    /* reflection target — see class comment */
  }

  @SuppressWarnings({"java:S1186", "java:S1144"})
  @McpResource(uri = "mocapi://resource")
  private void resourceMethod() {
    /* reflection target — see class comment */
  }

  @SuppressWarnings({"java:S1186", "java:S1144"})
  @McpResourceTemplate(uriTemplate = "mocapi://template/{id}")
  private void resourceTemplateMethod() {
    /* reflection target — see class comment */
  }

  @SuppressWarnings({"java:S1186", "java:S1144"})
  private void unannotatedMethod() {
    /* reflection target — see class comment */
  }

  @Test
  void kind_of_tool_method_is_tool() throws NoSuchMethodException {
    assertThat(HandlerKinds.kindOf(method("toolMethod"))).isEqualTo(HandlerKinds.KIND_TOOL);
  }

  @Test
  void kind_of_prompt_method_is_prompt() throws NoSuchMethodException {
    assertThat(HandlerKinds.kindOf(method("promptMethod"))).isEqualTo(HandlerKinds.KIND_PROMPT);
  }

  @Test
  void kind_of_resource_method_is_resource() throws NoSuchMethodException {
    assertThat(HandlerKinds.kindOf(method("resourceMethod"))).isEqualTo(HandlerKinds.KIND_RESOURCE);
  }

  @Test
  void kind_of_resource_template_method_is_resource_template() throws NoSuchMethodException {
    assertThat(HandlerKinds.kindOf(method("resourceTemplateMethod")))
        .isEqualTo(HandlerKinds.KIND_RESOURCE_TEMPLATE);
  }

  @Test
  void kind_of_unannotated_method_is_null() throws NoSuchMethodException {
    assertThat(HandlerKinds.kindOf(method("unannotatedMethod"))).isNull();
  }

  @Test
  void name_of_tool_method_is_annotation_name() throws NoSuchMethodException {
    assertThat(HandlerKinds.nameOf(method("toolMethod"))).isEqualTo("my-tool");
  }

  @Test
  void name_of_prompt_method_is_annotation_name() throws NoSuchMethodException {
    assertThat(HandlerKinds.nameOf(method("promptMethod"))).isEqualTo("my-prompt");
  }

  @Test
  void name_of_resource_method_is_annotation_uri() throws NoSuchMethodException {
    assertThat(HandlerKinds.nameOf(method("resourceMethod"))).isEqualTo("mocapi://resource");
  }

  @Test
  void name_of_resource_template_method_is_annotation_uri_template() throws NoSuchMethodException {
    assertThat(HandlerKinds.nameOf(method("resourceTemplateMethod")))
        .isEqualTo("mocapi://template/{id}");
  }

  @Test
  void name_of_unannotated_method_is_null() throws NoSuchMethodException {
    assertThat(HandlerKinds.nameOf(method("unannotatedMethod"))).isNull();
  }

  @Test
  void private_constructor_is_invocable_via_reflection() throws ReflectiveOperationException {
    Constructor<HandlerKinds> ctor = HandlerKinds.class.getDeclaredConstructor();
    ctor.setAccessible(true);
    assertThat(ctor.newInstance()).isInstanceOf(HandlerKinds.class);
  }

  private Method method(String name) throws NoSuchMethodException {
    return HandlerKindsTest.class.getDeclaredMethod(name);
  }
}
