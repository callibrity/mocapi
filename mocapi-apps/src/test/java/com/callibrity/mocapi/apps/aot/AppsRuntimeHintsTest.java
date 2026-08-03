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

import com.callibrity.mocapi.apps.McpUiResourceCsp;
import com.callibrity.mocapi.apps.McpUiToolMeta;
import com.callibrity.mocapi.apps.UiResourceMeta;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AppsRuntimeHintsTest {

  private final RuntimeHints hints = new RuntimeHints();

  {
    new AppsRuntimeHints().registerHints(hints, getClass().getClassLoader());
  }

  @Test
  void registers_hints_for_the_tool_meta_record() {
    assertTypeHintRegistered(McpUiToolMeta.class);
  }

  @Test
  void registers_hints_for_the_resource_meta_record_and_its_nested_csp() {
    assertTypeHintRegistered(UiResourceMeta.class);
    assertTypeHintRegistered(McpUiResourceCsp.class);
  }

  private void assertTypeHintRegistered(Class<?> type) {
    assertThat(hints.reflection().typeHints())
        .as("expected binding hints for %s", type.getName())
        .anyMatch(th -> th.getType().equals(TypeReference.of(type)));
  }
}
