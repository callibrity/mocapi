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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.prompts.PromptService;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.api.tools.ToolService;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiServicesAotProcessorTest {

  public record GreetArgs(String name) {}

  public record GreetResult(String message) {}

  @ToolService
  public static class SampleTool {
    @McpTool(name = "greet")
    public GreetResult greet(@McpToolParams GreetArgs args) {
      return new GreetResult("hi " + args.name());
    }

    @McpTool(name = "noop")
    public void noop() {
      // Fixture for the void-return-type branch of the AOT processor; tests assert no void.class
      // hint is emitted. The body never runs — only the declared signature matters.
    }

    @McpTool(name = "noop-boxed")
    public Void noopBoxed() {
      // Fixture for the boxed-Void-return-type branch; tests assert no Void.class hint is emitted.
      return null;
    }

    // Not annotated — must be ignored.
    public String unrelatedHelper() {
      return "";
    }
  }

  @ToolService
  @PromptService
  public static class SampleCombined {
    @McpTool(name = "tool-side")
    public GreetResult asTool(@McpToolParams GreetArgs args) {
      return new GreetResult(args.name());
    }

    @McpPrompt(name = "prompt-side")
    public GetPromptResult asPrompt(String who) {
      return new GetPromptResult(
          null, List.of(new PromptMessage(Role.USER, new TextContent(who, null))));
    }
  }

  @PromptService
  public static class SamplePrompt {
    @McpPrompt(name = "say-hi")
    public GetPromptResult sayHi(String name) {
      return new GetPromptResult(
          null, List.of(new PromptMessage(Role.USER, new TextContent("hi " + name, null))));
    }
  }

  @ResourceService
  public static class SampleResource {
    @McpResource(uri = "test://x")
    public ReadResourceResult fixed() {
      return ReadResourceResult.ofText("test://x", "text/plain", "x");
    }

    @McpResourceTemplate(uriTemplate = "test://items/{id}")
    public ReadResourceResult templated(String id) {
      return ReadResourceResult.ofText("test://items/" + id, "text/plain", id);
    }
  }

  public static class UnrelatedBean {
    public String noHints() {
      return "";
    }
  }

  private final MocapiServicesAotProcessor processor = new MocapiServicesAotProcessor();

  @Test
  void returns_null_for_unrelated_bean() {
    assertThat(processor.processAheadOfTime(registeredBean("x", UnrelatedBean.class))).isNull();
  }

  @Test
  void tool_service_registers_invoke_and_parameter_and_return_hints() {
    var hints = applyContribution(SampleTool.class);

    assertMethodHint(hints, SampleTool.class, "greet");
    assertMethodHint(hints, SampleTool.class, "noop");
    assertMethodHint(hints, SampleTool.class, "noopBoxed");
    assertTypeHintRegistered(hints, GreetArgs.class);
    assertTypeHintRegistered(hints, GreetResult.class);
  }

  @Test
  void tool_service_ignores_unannotated_methods() {
    var hints = applyContribution(SampleTool.class);

    assertThat(hints.reflection().typeHints())
        .filteredOn(th -> th.getType().equals(TypeReference.of(SampleTool.class)))
        .singleElement()
        .satisfies(
            th -> assertThat(th.methods()).noneMatch(m -> m.getName().equals("unrelatedHelper")));
  }

  @Test
  void skips_binding_hints_for_void_return() {
    var hints = applyContribution(SampleTool.class);

    assertTypeHintNotRegistered(hints, void.class);
  }

  @Test
  void skips_binding_hints_for_boxed_void_return() {
    var hints = applyContribution(SampleTool.class);

    assertTypeHintNotRegistered(hints, Void.class);
  }

  @Test
  void bean_with_multiple_service_annotations_registers_all_method_kinds() {
    var hints = applyContribution(SampleCombined.class);

    assertMethodHint(hints, SampleCombined.class, "asTool");
    assertMethodHint(hints, SampleCombined.class, "asPrompt");
  }

  @Test
  void prompt_service_registers_invoke_and_return_hints() {
    var hints = applyContribution(SamplePrompt.class);

    assertMethodHint(hints, SamplePrompt.class, "sayHi");
    assertTypeHintRegistered(hints, GetPromptResult.class);
  }

  @Test
  void resource_service_registers_both_resource_and_template_method_hints() {
    var hints = applyContribution(SampleResource.class);

    assertMethodHint(hints, SampleResource.class, "fixed");
    assertMethodHint(hints, SampleResource.class, "templated");
    assertTypeHintRegistered(hints, ReadResourceResult.class);
  }

  private RuntimeHints applyContribution(Class<?> beanClass) {
    var bean = registeredBean("bean", beanClass);
    BeanRegistrationAotContribution contribution = processor.processAheadOfTime(bean);
    assertThat(contribution).isNotNull();

    var hints = new RuntimeHints();
    var genContext = mock(GenerationContext.class);
    when(genContext.getRuntimeHints()).thenReturn(hints);
    contribution.applyTo(genContext, null);
    return hints;
  }

  private static void assertMethodHint(RuntimeHints hints, Class<?> owner, String methodName) {
    assertThat(hints.reflection().typeHints())
        .filteredOn(th -> th.getType().equals(TypeReference.of(owner)))
        .singleElement()
        .satisfies(
            th ->
                assertThat(th.methods())
                    .anyMatch(
                        m ->
                            m.getName().equals(methodName)
                                && m.getMode() == ExecutableMode.INVOKE));
  }

  private static void assertTypeHintRegistered(RuntimeHints hints, Class<?> type) {
    assertThat(hints.reflection().typeHints())
        .anyMatch(th -> th.getType().equals(TypeReference.of(type)));
  }

  private static void assertTypeHintNotRegistered(RuntimeHints hints, Class<?> type) {
    assertThat(hints.reflection().typeHints())
        .noneMatch(th -> th.getType().equals(TypeReference.of(type)));
  }

  private static RegisteredBean registeredBean(String name, Class<?> type) {
    var factory = new DefaultListableBeanFactory();
    factory.registerBeanDefinition(name, new RootBeanDefinition(type));
    return RegisteredBean.of(factory, name);
  }
}
