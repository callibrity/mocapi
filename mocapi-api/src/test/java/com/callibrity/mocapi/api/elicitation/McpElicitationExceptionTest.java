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
package com.callibrity.mocapi.api.elicitation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpElicitationExceptionTest {

  @Test
  void single_arg_constructor_should_set_message() {
    var ex = new McpElicitationException("elicitation failed");
    assertThat(ex).hasMessage("elicitation failed").hasNoCause();
  }

  @Test
  void two_arg_constructor_should_set_message_and_cause() {
    var cause = new RuntimeException("root cause");
    var ex = new McpElicitationException("elicitation failed", cause);
    assertThat(ex).hasMessage("elicitation failed").hasCause(cause);
  }
}
