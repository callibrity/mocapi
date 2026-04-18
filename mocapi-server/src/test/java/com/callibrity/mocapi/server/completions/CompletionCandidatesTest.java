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
package com.callibrity.mocapi.server.completions;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompletionCandidatesTest {

  enum Detail {
    BRIEF,
    STANDARD,
    DETAILED
  }

  static class Fixture {
    void enumParam(Detail detail) {}

    void schemaParam(@Schema(allowableValues = {"a", "b", "c"}) String value) {}

    void enumAndSchema(@Schema(allowableValues = {"x", "y"}) Detail both) {}

    void plainString(String free) {}
  }

  @Test
  void extracts_enum_constants_in_declaration_order() {
    var values = CompletionCandidates.valuesFor(parameter("enumParam", Detail.class));
    assertThat(values).containsExactly("BRIEF", "STANDARD", "DETAILED");
  }

  @Test
  void extracts_schema_allowable_values_for_string() {
    var values = CompletionCandidates.valuesFor(parameter("schemaParam", String.class));
    assertThat(values).containsExactly("a", "b", "c");
  }

  @Test
  void enum_wins_over_schema_when_both_are_present() {
    var values = CompletionCandidates.valuesFor(parameter("enumAndSchema", Detail.class));
    assertThat(values).containsExactly("BRIEF", "STANDARD", "DETAILED");
  }

  @Test
  void returns_empty_for_plain_string_without_schema() {
    var values = CompletionCandidates.valuesFor(parameter("plainString", String.class));
    assertThat(values).isEmpty();
  }

  private static Parameter parameter(String methodName, Class<?> paramType) {
    try {
      Method m = Fixture.class.getDeclaredMethod(methodName, paramType);
      return m.getParameters()[0];
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }
}
