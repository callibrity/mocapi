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
package com.callibrity.mocapi.api.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.progress.CountingProgressEmitter;
import com.callibrity.mocapi.api.progress.DoubleProgressEmitter;
import com.callibrity.mocapi.api.progress.LongProgressEmitter;
import com.callibrity.mocapi.api.progress.PercentageCompleteProgressEmitter;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolContextDefaultMethodsTest {

  /** Test double that records the params handed to {@link #elicit(ElicitRequestFormParams)}. */
  static class CapturingElicitContext implements McpToolContext {
    ElicitRequestFormParams lastElicitParams;

    @Override
    public String handlerName() {
      return "mcp";
    }

    @Override
    public DoubleProgressEmitter doubleProgress(Double total) {
      return null; // not under test
    }

    @Override
    public LongProgressEmitter longProgress(Long total) {
      return null; // not under test
    }

    @Override
    public CountingProgressEmitter countingProgress(Long total) {
      return null; // not under test
    }

    @Override
    public PercentageCompleteProgressEmitter percentProgress() {
      return null; // not under test
    }

    @Override
    public ElicitResult elicit(ElicitRequestFormParams params) {
      lastElicitParams = params;
      return new ElicitResult(ElicitAction.ACCEPT, null);
    }
  }

  @Nested
  class Fluent_elicit {

    @Test
    void fluent_elicit_builds_schema_and_delegates_to_elicit() {
      var ctx = new CapturingElicitContext();

      ctx.elicit(
          "Enter your info",
          schema -> schema.string("name", "Your name").string("email", "Email", s -> s.email()));

      assertThat(ctx.lastElicitParams).isNotNull();
      assertThat(ctx.lastElicitParams.message()).isEqualTo("Enter your info");
      assertThat(ctx.lastElicitParams.requestedSchema()).isNotNull();
      assertThat(ctx.lastElicitParams.requestedSchema().properties()).containsKeys("name", "email");
      assertThat(ctx.lastElicitParams.requestedSchema().required()).contains("name", "email");
    }

    @Test
    void fluent_elicit_with_optional_field_excludes_from_required() {
      var ctx = new CapturingElicitContext();

      ctx.elicit(
          "Optional test",
          schema ->
              schema
                  .string("required", "Required")
                  .string("optional", "Optional", s -> s.optional()));

      assertThat(ctx.lastElicitParams.requestedSchema().required()).containsExactly("required");
    }
  }
}
