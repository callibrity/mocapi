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
package com.callibrity.mocapi.server.tools.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.Test;

class ParametersTest {

  // --- Reflective targets ---

  void plainParam(String value) {
    // Reflective target — parameter metadata used by tests
  }

  void schemaWithBlankDescription(@Schema(description = "  ") String value) {
    // Reflective target — blank @Schema description
  }

  void schemaWithBlankTitle(@Schema(title = "  ") String value) {
    // Reflective target — blank @Schema title
  }

  void schemaWithTitle(@Schema(title = "Custom Title") String value) {
    // Reflective target — non-blank @Schema title
  }

  // --- Tests ---

  @Test
  void descriptionOfShouldReturnNullWhenSchemaDescriptionIsBlank() throws Exception {
    Parameter param =
        getClass().getDeclaredMethod("schemaWithBlankDescription", String.class).getParameters()[0];
    assertThat(Parameters.descriptionOf(param)).isNull();
  }

  @Test
  void titleOfShouldFallBackToCapitalizedNameWhenSchemaTitleIsBlank() throws Exception {
    Parameter param =
        getClass().getDeclaredMethod("schemaWithBlankTitle", String.class).getParameters()[0];
    // Falls back to Names.capitalizedWords(parameter.getName()) — the parameter name from bytecode
    assertThat(Parameters.titleOf(param)).isNotBlank();
  }

  @Test
  void titleOfShouldReturnSchemaTitleWhenPresent() throws Exception {
    Parameter param =
        getClass().getDeclaredMethod("schemaWithTitle", String.class).getParameters()[0];
    assertThat(Parameters.titleOf(param)).isEqualTo("Custom Title");
  }
}
