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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class McpTaskAnnotationTest {

  static class Fixture {
    @McpTask
    public void defaults() {
      // Reflection fixture: only the annotation is read; the body is intentionally empty.
    }

    @McpTask(ttl = "PT30M", pollInterval = "PT5S", required = true)
    public void overridden() {
      // Reflection fixture: only the annotation is read; the body is intentionally empty.
    }
  }

  @Test
  void is_retained_at_runtime() {
    Retention retention = McpTask.class.getAnnotation(Retention.class);
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void targets_methods_and_annotation_types() {
    Target target = McpTask.class.getAnnotation(Target.class);
    assertThat(target.value())
        .containsExactlyInAnyOrder(ElementType.METHOD, ElementType.ANNOTATION_TYPE);
  }

  @Test
  void is_documented() {
    assertThat(McpTask.class.getAnnotation(Documented.class)).isNotNull();
  }

  @Test
  void defaults_to_empty_ttl_and_poll_interval_and_not_required() throws Exception {
    Method m = Fixture.class.getMethod("defaults");
    McpTask task = m.getAnnotation(McpTask.class);
    assertThat(task.ttl()).isEmpty();
    assertThat(task.pollInterval()).isEmpty();
    assertThat(task.required()).isFalse();
  }

  @Test
  void carries_overridden_attribute_values() throws Exception {
    Method m = Fixture.class.getMethod("overridden");
    McpTask task = m.getAnnotation(McpTask.class);
    assertThat(task.ttl()).isEqualTo("PT30M");
    assertThat(task.pollInterval()).isEqualTo("PT5S");
    assertThat(task.required()).isTrue();
  }
}
