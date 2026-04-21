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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HandlerDescriptorTest {

  @Test
  void canonical_constructor_copies_interceptors_to_unmodifiable_list() {
    var source = new ArrayList<String>();
    source.add("one");
    var descriptor = new HandlerDescriptor(HandlerKind.TOOL, "com.example.Foo", "bar", source);

    source.add("mutated");

    var frozen = descriptor.interceptors();
    assertThat(frozen).containsExactly("one");
    assertThatThrownBy(() -> frozen.add("nope")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void record_accessors_return_configured_values() {
    var descriptor =
        new HandlerDescriptor(
            HandlerKind.RESOURCE_TEMPLATE, "com.example.Foo", "bar", List.of("i1"));
    assertThat(descriptor.kind()).isEqualTo(HandlerKind.RESOURCE_TEMPLATE);
    assertThat(descriptor.declaringClassName()).isEqualTo("com.example.Foo");
    assertThat(descriptor.methodName()).isEqualTo("bar");
    assertThat(descriptor.interceptors()).containsExactly("i1");
  }
}
