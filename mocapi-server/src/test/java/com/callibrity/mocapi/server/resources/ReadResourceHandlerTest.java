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
package com.callibrity.mocapi.server.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReadResourceHandlerTest {

  private final MethodInvokerFactory invokerFactory = new DefaultMethodInvokerFactory();

  public static class Fixture {
    @McpResource(
        uri = "test://hello",
        name = "Hello",
        description = "hello",
        mimeType = "text/plain")
    public ReadResourceResult hello() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://hello", "text/plain", "hi")));
    }
  }

  public static class DefaultedFixture {
    @McpResource(uri = "test://defaulted")
    public ReadResourceResult defaulted() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://defaulted", "text/plain", "ok")));
    }
  }

  public static class BadResource {
    @McpResource(uri = "test://bad")
    public String oops() {
      return "nope";
    }
  }

  @Test
  void discover_builds_handler_from_annotated_method() {
    var handler =
        ReadResourceHandlers.discover(new Fixture(), invokerFactory, List.of(), s -> s).getFirst();

    assertThat(handler.uri()).isEqualTo("test://hello");
    assertThat(handler.descriptor().name()).isEqualTo("Hello");
    assertThat(handler.descriptor().description()).isEqualTo("hello");
    assertThat(handler.descriptor().mimeType()).isEqualTo("text/plain");
    assertThat(handler.method().getName()).isEqualTo("hello");
    assertThat(handler.bean()).isInstanceOf(Fixture.class);
  }

  @Test
  void read_invokes_underlying_method() {
    var handler =
        ReadResourceHandlers.discover(new Fixture(), invokerFactory, List.of(), s -> s).getFirst();

    var result = handler.read();

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("hi");
  }

  @Test
  void name_and_description_default_when_annotation_values_are_blank() {
    var handler =
        ReadResourceHandlers.discover(new DefaultedFixture(), invokerFactory, List.of(), s -> s)
            .getFirst();

    assertThat(handler.descriptor().name()).isNotBlank();
    assertThat(handler.descriptor().description()).isEqualTo(handler.descriptor().name());
    assertThat(handler.descriptor().mimeType()).isNull();
  }

  @Test
  void resource_method_with_non_result_return_type_is_rejected() {
    var target = new BadResource();
    assertThatThrownBy(
            () -> ReadResourceHandlers.discover(target, invokerFactory, List.of(), s -> s))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReadResourceResult");
  }
}
