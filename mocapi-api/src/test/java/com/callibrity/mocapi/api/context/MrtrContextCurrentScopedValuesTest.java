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
package com.callibrity.mocapi.api.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.prompts.McpPromptContext;
import com.callibrity.mocapi.api.resources.McpResourceContext;
import com.callibrity.mocapi.api.tools.McpToolContext;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Each MRTR-capable handler kind exposes its own {@code CURRENT} {@link ScopedValue} so a tool,
 * prompt, or resource handler can only ever bind and read the context of its own kind — a
 * cross-kind mix-up (binding {@code McpToolContext.CURRENT} but reading {@code
 * McpPromptContext.CURRENT}) must fail to compile or, at worst, find nothing bound, never resolve
 * to another kind's context by accident.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MrtrContextCurrentScopedValuesTest {

  @Test
  void each_handler_kind_has_its_own_distinct_current_scoped_value() {
    assertThat(McpToolContext.CURRENT).isNotNull();
    assertThat(McpPromptContext.CURRENT).isNotNull();
    assertThat(McpResourceContext.CURRENT).isNotNull();

    assertThat(McpToolContext.CURRENT)
        .isNotSameAs(McpPromptContext.CURRENT)
        .isNotSameAs(McpResourceContext.CURRENT);
    assertThat(McpPromptContext.CURRENT).isNotSameAs(McpResourceContext.CURRENT);
  }

  @Test
  void a_scoped_value_unbound_in_the_current_thread_reports_unbound_not_a_stale_leftover() {
    // Nothing has bound any of these CURRENT values on the test thread; each must report "not
    // bound" rather than resolving to a value left over from some other execution.
    assertThat(McpToolContext.CURRENT.isBound()).isFalse();
    assertThat(McpPromptContext.CURRENT.isBound()).isFalse();
    assertThat(McpResourceContext.CURRENT.isBound()).isFalse();
  }
}
