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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.apps.McpUi;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The server half of an MCP App, in the leanest form: a single {@code get-time} tool that both does
 * the work and declares its UI. {@code @McpUi(resource=…)} serves the app's self-contained HTML/JS
 * bundle at {@link #RESOURCE_URI} straight from the classpath (ADR-0036) — no resource method to
 * write — and links the tool to it via the tool descriptor's {@code _meta.ui.resourceUri}. As a
 * plain {@code @Component} it is component-scanned and discovered by mocapi's handler scan; no
 * example-specific auto-configuration is needed.
 *
 * <p>mocapi is only the server: it serves the HTML and the metadata. The interactive layer (the
 * {@code postMessage} / {@code ui-initialize} handshake, the "Get Server Time" button calling back
 * to this tool) lives entirely in the in-iframe JavaScript, which the host runs. The UI is the
 * small React app under {@code src/main/frontend} built into the served bundle (see the module
 * README for provenance); the tool name ({@code get-time}) and the {@code {time}} structured result
 * match what that app calls and renders.
 */
@Component
public class GetTimeApp {

  static final String RESOURCE_URI = "ui://get-time/mcp-app.html";

  /**
   * The tool the app calls; its {@code {time}} result is rendered inside the linked UI, whose
   * bundle mocapi serves from the classpath.
   */
  @McpTool(
      name = "get-time",
      description = "Returns the current server time as an ISO 8601 string.")
  @McpUi(value = RESOURCE_URI, resource = "classpath:/ui/get-time/mcp-app.html")
  public TimeResult getTime() {
    return new TimeResult(Instant.now().toString());
  }

  /** Structured tool result; serializes to {@code {"time": "..."}}, which the app reads. */
  public record TimeResult(String time) {}
}
