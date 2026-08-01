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
package com.callibrity.mocapi.examples.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.callibrity.mocapi.apps.McpUi;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GetTimeAppTest {

  private final GetTimeApp app = new GetTimeApp();

  @Test
  void get_time_returns_a_parseable_iso_instant() {
    var result = app.getTime();
    assertThat(result.time()).isNotBlank();
    assertThatCode(() -> Instant.parse(result.time())).doesNotThrowAnyException();
  }

  @Test
  void mcpui_links_the_tool_to_a_serve_mode_bundle() throws Exception {
    McpUi ui = GetTimeApp.class.getMethod("getTime").getAnnotation(McpUi.class);
    assertThat(ui.value()).isEqualTo("ui://get-time/mcp-app.html");
    assertThat(ui.resource()).isEqualTo("classpath:/ui/get-time/mcp-app.html");
  }

  @Test
  void the_served_bundle_is_on_the_classpath_with_the_app_content() throws Exception {
    try (InputStream in = GetTimeApp.class.getResourceAsStream("/ui/get-time/mcp-app.html")) {
      assertThat(in).as("vendored UI bundle on the classpath").isNotNull();
      String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(html).contains("<title>Get Time App</title>");
    }
  }
}
