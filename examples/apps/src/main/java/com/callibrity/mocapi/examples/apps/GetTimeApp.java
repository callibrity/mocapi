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
import com.callibrity.mocapi.apps.McpAppResource;
import com.callibrity.mocapi.apps.McpUi;
import com.callibrity.mocapi.model.ReadResourceResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * The server half of an MCP App. It exposes two linked handlers:
 *
 * <ul>
 *   <li>{@link #mcpApp()} — a {@code ui://} resource that returns the app's self-contained HTML/JS
 *       bundle ({@code text/html;profile=mcp-app}).
 *   <li>{@link #getTime()} — a {@code get-time} tool whose result the app renders; {@code @McpUi}
 *       links it to the resource above via the tool descriptor's {@code _meta.ui.resourceUri}.
 * </ul>
 *
 * <p>mocapi is only the server: it serves the HTML and the metadata. The interactive layer (the
 * {@code postMessage} / {@code ui-initialize} handshake, the "Get Server Time" button calling back
 * to this tool) lives entirely in the in-iframe JavaScript, which the host runs. The bundle here is
 * the official ext-apps vanilla-JS "Get Time" app, vendored verbatim (see the module README for
 * provenance); the tool name ({@code get-time}) and the {@code {time}} structured result match what
 * that bundle calls and renders.
 */
public class GetTimeApp {

  static final String RESOURCE_URI = "ui://get-time/mcp-app.html";
  private static final String UI_MIME_TYPE = "text/html;profile=mcp-app";
  private static final String BUNDLE_PATH = "/ui/get-time/mcp-app.html";

  private final String html;

  public GetTimeApp() {
    this.html = loadBundle();
  }

  /** Serves the interactive Get Time app UI as a {@code ui://} MCP Apps resource. */
  @McpAppResource(uri = RESOURCE_URI, name = "Get Time App")
  public ReadResourceResult mcpApp() {
    return ReadResourceResult.ofText(RESOURCE_URI, UI_MIME_TYPE, html);
  }

  /** The tool the app calls; its {@code {time}} result is rendered inside the linked UI. */
  @McpTool(
      name = "get-time",
      description = "Returns the current server time as an ISO 8601 string.")
  @McpUi(RESOURCE_URI)
  public TimeResult getTime() {
    return new TimeResult(Instant.now().toString());
  }

  /** Structured tool result; serializes to {@code {"time": "..."}}, which the app reads. */
  public record TimeResult(String time) {}

  private static String loadBundle() {
    try (InputStream in = GetTimeApp.class.getResourceAsStream(BUNDLE_PATH)) {
      if (in == null) {
        throw new IllegalStateException("Missing UI bundle on classpath: " + BUNDLE_PATH);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read UI bundle " + BUNDLE_PATH, e);
    }
  }
}
