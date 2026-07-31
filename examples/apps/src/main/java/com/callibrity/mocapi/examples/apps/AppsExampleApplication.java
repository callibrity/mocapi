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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runnable mocapi example demonstrating the MCP Apps extension over Streamable HTTP. Boots an MCP
 * server that serves an interactive {@code ui://} HTML resource linked to a {@code get-time} tool;
 * point an MCP Apps host (e.g. a compatible desktop client) at {@code http://localhost:8080/mcp} to
 * render it. See the module README.
 */
@SpringBootApplication
public class AppsExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(AppsExampleApplication.class, args);
  }
}
