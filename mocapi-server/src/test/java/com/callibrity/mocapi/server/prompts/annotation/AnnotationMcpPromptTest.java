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
package com.callibrity.mocapi.server.prompts.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.prompts.PromptMethod;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.util.StringMapArgResolver;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.core.convert.support.DefaultConversionService;

class AnnotationMcpPromptTest {

  private final MethodInvokerFactory invokerFactory = new DefaultMethodInvokerFactory(List.of());
  private final List<ParameterResolver<? super Map<String, String>>> resolvers =
      List.of(new StringMapArgResolver(DefaultConversionService.getSharedInstance()));

  private List<AnnotationMcpPrompt> createPrompts(Object target) {
    return AnnotationMcpPrompt.createPrompts(invokerFactory, resolvers, target);
  }

  enum Detail {
    BRIEF,
    STANDARD,
    DETAILED
  }

  public static class SummarizePrompt {
    @PromptMethod(
        name = "summarize",
        title = "Summarize",
        description = "Summarize text at a specified detail level")
    public GetPromptResult summarize(
        @Schema(description = "The text to summarize") String text,
        @Schema(description = "Detail level") @Nullable Detail detail) {
      var level = detail == null ? Detail.STANDARD : detail;
      return new GetPromptResult(
          "summary",
          List.of(new PromptMessage(Role.USER, new TextContent(level + ": " + text, null))));
    }
  }

  public static class WholeMapPrompt {
    @PromptMethod(name = "raw")
    public GetPromptResult raw(Map<String, String> args) {
      return new GetPromptResult(
          "raw", List.of(new PromptMessage(Role.USER, new TextContent(args.toString(), null))));
    }
  }

  public static class BadReturnPrompt {
    @PromptMethod(name = "oops")
    public String oops() {
      return "nope";
    }
  }

  @Test
  void generatesDescriptorFromAnnotationAndParameters() {
    var prompt = createPrompts(new SummarizePrompt()).getFirst();

    var d = prompt.descriptor();
    assertThat(d.name()).isEqualTo("summarize");
    assertThat(d.title()).isEqualTo("Summarize");
    assertThat(d.description()).isEqualTo("Summarize text at a specified detail level");
    assertThat(d.arguments()).hasSize(2);
    assertThat(d.arguments().get(0).name()).isEqualTo("text");
    assertThat(d.arguments().get(0).required()).isTrue();
    assertThat(d.arguments().get(1).name()).isEqualTo("detail");
    assertThat(d.arguments().get(1).required()).isFalse();
  }

  @Test
  void invokesMethodWithConvertedArgs() {
    var prompt = createPrompts(new SummarizePrompt()).getFirst();

    var result = prompt.get(Map.of("text", "hello world", "detail", "BRIEF"));

    assertThat(result.messages()).hasSize(1);
    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("BRIEF: hello world");
  }

  @Test
  void missingOptionalArgComesThroughAsNull() {
    var prompt = createPrompts(new SummarizePrompt()).getFirst();

    var result = prompt.get(Map.of("text", "hi"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("STANDARD: hi");
  }

  @Test
  void mapTypedParamReceivesWholeArgumentsMap() {
    var prompt = createPrompts(new WholeMapPrompt()).getFirst();

    var result = prompt.get(Map.of("a", "1", "b", "2"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).contains("a=1").contains("b=2");
  }

  @Test
  void getWithNullArgumentsInvokesWithEmptyMap() {
    var prompt = createPrompts(new WholeMapPrompt()).getFirst();

    var result = prompt.get(null);

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("{}");
  }

  @Test
  void rejectsMethodWithNonGetPromptResultReturnType() {
    var target = new BadReturnPrompt();
    assertThatThrownBy(() -> createPrompts(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetPromptResult");
  }
}
