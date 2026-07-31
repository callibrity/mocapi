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

import com.callibrity.mocapi.apps.McpAppResource;
import com.callibrity.mocapi.apps.McpUi;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
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
  void mcp_app_serves_the_ui_bundle_with_the_apps_mime_type() {
    ReadResourceResult result = app.mcpApp();
    var contents = (TextResourceContents) result.contents().getFirst();
    assertThat(contents.uri()).isEqualTo("ui://get-time/mcp-app.html");
    assertThat(contents.mimeType()).isEqualTo("text/html;profile=mcp-app");
    assertThat(contents.text()).contains("<title>Get Time App</title>");
  }

  @Test
  void tool_ui_link_matches_the_served_resource_uri() throws Exception {
    String linkedUri = GetTimeApp.class.getMethod("getTime").getAnnotation(McpUi.class).value();
    String resourceUri =
        GetTimeApp.class.getMethod("mcpApp").getAnnotation(McpAppResource.class).uri();
    assertThat(linkedUri).isEqualTo(resourceUri).isEqualTo("ui://get-time/mcp-app.html");
  }
}
