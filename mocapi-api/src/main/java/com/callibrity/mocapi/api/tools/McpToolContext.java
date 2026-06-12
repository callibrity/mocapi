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
package com.callibrity.mocapi.api.tools;

import com.callibrity.mocapi.api.elicitation.McpElicitor;

/**
 * Context available to tool methods that need mid-execution communication with the client. Provides
 * progress notifications and (via {@link McpElicitor}) form-mode elicitation. Tools return their
 * final result via the method return value — this context is only for mid-execution communication.
 *
 * <p>MCP 2026-07-28 deprecates Sampling and MCP Logging (SEP-2577), so the former {@code
 * sample(...)} and {@code logger(...)} surfaces are gone. Per the spec's migration guidance:
 * integrate directly with your LLM provider's API instead of sampling, and use stderr (stdio) or
 * OpenTelemetry — which {@code mocapi-otel} covers — instead of MCP logging. See ADR-0022.
 *
 * <p>Example elicitation (inherited from {@link McpElicitor}; see ADR-0024):
 *
 * <pre>{@code
 * ElicitResult result = ctx.elicit("Please enter your details", schema -> schema
 *     .string("name", "Your name")
 *     .string("email", "Email address", s -> s.email())
 *     .integer("age", "Your age", s -> s.optional().min(0).max(150))
 * );
 * }</pre>
 */
public interface McpToolContext extends McpElicitor {

  ScopedValue<McpToolContext> CURRENT = ScopedValue.newInstance();

  /**
   * Sends a progress notification to the client.
   *
   * @param progress the current progress value
   * @param total the total expected value
   */
  void sendProgress(long progress, long total);

  /**
   * Returns the name of the handler currently executing (the {@code @McpTool} name, or the prompt /
   * resource name).
   *
   * @return the current handler name
   */
  String handlerName();
}
