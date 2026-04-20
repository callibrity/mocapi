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
package com.callibrity.mocapi.security.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.security.spring.RequiresRole;
import com.callibrity.mocapi.security.spring.RequiresScope;
import com.callibrity.mocapi.support.LogCaptor;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubPromptConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubResourceConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubResourceTemplateConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubToolConfig;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiSpringSecurityGuardsAttachmentLogTest {

  private final MocapiSpringSecurityGuardsAutoConfiguration autoConfig =
      new MocapiSpringSecurityGuardsAutoConfiguration();

  @Test
  void tool_customizer_logs_scope_guard_attachment() {
    var method = methodOf("scoped");
    var config = new StubToolConfig(new Tool("scoped-tool", null, null, null, null), method);

    try (var captor = LogCaptor.forClass(MocapiSpringSecurityGuardsAutoConfiguration.class)) {
      autoConfig.springSecurityToolGuardCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached ScopeGuard guard to tool \"scoped-tool\"");
    }
  }

  @Test
  void tool_customizer_logs_both_guards_when_both_annotations_present() {
    var method = methodOf("scopedAndRoled");
    var config = new StubToolConfig(new Tool("dual-tool", null, null, null, null), method);

    try (var captor = LogCaptor.forClass(MocapiSpringSecurityGuardsAutoConfiguration.class)) {
      autoConfig.springSecurityToolGuardCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly(
              "Attached ScopeGuard guard to tool \"dual-tool\"",
              "Attached RoleGuard guard to tool \"dual-tool\"");
    }
  }

  @Test
  void tool_customizer_is_silent_when_no_annotations_present() {
    var method = methodOf("plain");
    var config = new StubToolConfig(new Tool("plain-tool", null, null, null, null), method);

    try (var captor = LogCaptor.forClass(MocapiSpringSecurityGuardsAutoConfiguration.class)) {
      autoConfig.springSecurityToolGuardCustomizer().customize(config);
      assertThat(captor.formattedMessages()).isEmpty();
    }
  }

  @Test
  void prompt_customizer_logs_role_guard_attachment() {
    var method = methodOf("roled");
    var config = new StubPromptConfig(new Prompt("my-prompt", null, null, null, null), method);

    try (var captor = LogCaptor.forClass(MocapiSpringSecurityGuardsAutoConfiguration.class)) {
      autoConfig.springSecurityPromptGuardCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached RoleGuard guard to prompt \"my-prompt\"");
    }
  }

  @Test
  void resource_customizer_logs_scope_guard_attachment() {
    var method = methodOf("scoped");
    var config = new StubResourceConfig(new Resource("mem://x", "x", null, null), method);

    try (var captor = LogCaptor.forClass(MocapiSpringSecurityGuardsAutoConfiguration.class)) {
      autoConfig.springSecurityResourceGuardCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached ScopeGuard guard to resource \"mem://x\"");
    }
  }

  @Test
  void resource_template_customizer_logs_scope_guard_attachment() {
    var method = methodOf("scoped");
    var config =
        new StubResourceTemplateConfig(
            new ResourceTemplate("mem://x/{id}", "x", null, null), method);

    try (var captor = LogCaptor.forClass(MocapiSpringSecurityGuardsAutoConfiguration.class)) {
      autoConfig.springSecurityResourceTemplateGuardCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached ScopeGuard guard to resource_template \"mem://x/{id}\"");
    }
  }

  private static Method methodOf(String name) {
    try {
      return Fixture.class.getDeclaredMethod(name);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  static final class Fixture {
    /** Fixture target annotated with {@link RequiresScope} only. */
    @RequiresScope("read")
    void scoped() {}

    /** Fixture target annotated with {@link RequiresRole} only. */
    @RequiresRole("USER")
    void roled() {}

    /** Fixture target annotated with both {@link RequiresScope} and {@link RequiresRole}. */
    @RequiresScope("read")
    @RequiresRole("USER")
    void scopedAndRoled() {}

    /** Fixture target with no security annotations. */
    void plain() {}
  }
}
