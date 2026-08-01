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
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.resources.McpResource;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

class AnnotationContractTest {

  static class Fixture {
    @McpAppResource(
        uri = "ui://dash",
        name = "Dash",
        csp = @Csp(connect = "https://api.example.com"))
    public void ui() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    @McpUi(
        value = "ui://dash",
        visibility = {"app"})
    public void tool() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }
  }

  @Test
  void app_resource_merges_mcp_resource_with_ui_mime_and_aliased_uri() throws Exception {
    Method m = Fixture.class.getMethod("ui");
    McpResource merged = AnnotatedElementUtils.findMergedAnnotation(m, McpResource.class);
    assertThat(merged).isNotNull();
    assertThat(merged.uri()).isEqualTo("ui://dash");
    assertThat(merged.mimeType()).isEqualTo("text/html;profile=mcp-app");
  }

  @Test
  void app_resource_exposes_csp() throws Exception {
    Method m = Fixture.class.getMethod("ui");
    McpAppResource app = m.getAnnotation(McpAppResource.class);
    assertThat(app.csp().connect()).containsExactly("https://api.example.com");
  }

  @Test
  void mcp_ui_carries_resource_uri_and_visibility() throws Exception {
    Method m = Fixture.class.getMethod("tool");
    McpUi ui = m.getAnnotation(McpUi.class);
    assertThat(ui.value()).isEqualTo("ui://dash");
    assertThat(ui.visibility()).containsExactly("app");
  }
}
