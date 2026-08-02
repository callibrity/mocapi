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
package com.callibrity.mocapi.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.MissingRequiredClientCapabilityErrorData;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskRequiredExceptionTranslatorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final TaskRequiredExceptionTranslator translator =
      new TaskRequiredExceptionTranslator(mapper);

  @Test
  void translates_to_the_missing_required_client_capability_error_code() {
    var exception = new McpTaskRequiredException("demo.tool");

    JsonRpcErrorDetail detail = translator.translate(exception);

    assertThat(detail.code()).isEqualTo(MissingRequiredClientCapabilityErrorData.CODE);
    assertThat(detail.code()).isEqualTo(-32021);
    assertThat(detail.message()).isEqualTo(exception.getMessage());
    assertThat(
            detail
                .data()
                .path("requiredCapabilities")
                .path("extensions")
                .has(TasksExtension.EXTENSION_ID))
        .isTrue();
  }
}
