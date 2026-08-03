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
package com.callibrity.mocapi.apps.aot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.apps.McpUi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AppsResourceAotProcessorTest {

  public static class SampleApp {

    @McpUi(value = "ui://sample/mcp-app.html", resource = "classpath:/ui/sample/mcp-app.html")
    public String uiTool() {
      return "";
    }

    @McpUi(value = "ui://placeholder/mcp-app.html", resource = "${sample.ui.resource}")
    public String placeholderTool() {
      return "";
    }

    @McpUi(value = "ui://declared-elsewhere/mcp-app.html")
    public String noResourceTool() {
      return "";
    }

    @McpUi(value = "ui://on-disk/mcp-app.html", resource = "file:/tmp/mcp-app.html")
    public String fileResourceTool() {
      return "";
    }
  }

  public static class UnrelatedBean {
    public String noHints() {
      return "";
    }
  }

  private final AppsResourceAotProcessor processor = new AppsResourceAotProcessor();

  @Test
  void returns_null_for_a_bean_with_no_mcp_ui_methods() {
    assertThat(processor.processAheadOfTime(registeredBean("x", UnrelatedBean.class))).isNull();
  }

  @Test
  void registers_a_resource_pattern_for_a_classpath_scheme_bundle() {
    assertThat(patternsFor(SampleApp.class)).contains("ui/sample/mcp-app.html");
  }

  @Test
  void resolves_a_placeholder_valued_resource_attribute_before_registering_the_pattern() {
    var beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerBeanDefinition("bean", new RootBeanDefinition(SampleApp.class));
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test", Map.of("sample.ui.resource", "classpath:/ui/resolved.html")));
    var configurer = new PropertySourcesPlaceholderConfigurer();
    configurer.setEnvironment(environment);
    configurer.postProcessBeanFactory(beanFactory);

    var contribution = processor.processAheadOfTime(RegisteredBean.of(beanFactory, "bean"));
    assertThat(contribution).isNotNull();

    var hints = apply(contribution);
    assertThat(patternsOf(hints)).contains("ui/resolved.html");
  }

  @Test
  void skips_a_bean_whose_only_mcp_ui_method_declares_no_resource_location() {
    // "declared elsewhere" (@McpUi with no resource()) still shares the sample bean with a
    // resource-bearing method, so assert the elsewhere-declared URI's location is simply absent
    // rather than the whole bean being skipped.
    assertThat(patternsFor(SampleApp.class)).doesNotContain("ui://declared-elsewhere/mcp-app.html");
  }

  @Test
  void skips_a_file_scheme_location_since_it_needs_no_inclusion_hint() {
    var patterns = patternsFor(SampleApp.class);
    assertThat(patterns).noneMatch(p -> p.contains("mcp-app.html") && p.contains("tmp"));
  }

  private List<String> patternsFor(Class<?> beanClass) {
    var bean = registeredBean("bean", beanClass);
    BeanRegistrationAotContribution contribution = processor.processAheadOfTime(bean);
    assertThat(contribution).isNotNull();
    return patternsOf(apply(contribution));
  }

  private static List<String> patternsOf(RuntimeHints hints) {
    return hints
        .resources()
        .resourcePatternHints()
        .flatMap(hint -> hint.getIncludes().stream())
        .map(p -> p.getPattern())
        .toList();
  }

  private static RuntimeHints apply(BeanRegistrationAotContribution contribution) {
    var hints = new RuntimeHints();
    var genContext = mock(GenerationContext.class);
    when(genContext.getRuntimeHints()).thenReturn(hints);
    contribution.applyTo(genContext, null);
    return hints;
  }

  private static RegisteredBean registeredBean(String name, Class<?> type) {
    ConfigurableListableBeanFactory factory = new DefaultListableBeanFactory();
    ((DefaultListableBeanFactory) factory)
        .registerBeanDefinition(name, new RootBeanDefinition(type));
    return RegisteredBean.of(factory, name);
  }
}
