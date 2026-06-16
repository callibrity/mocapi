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
package com.callibrity.mocapi.api.context;

import com.callibrity.mocapi.api.elicitation.McpElicitor;
import com.callibrity.mocapi.api.progress.McpProgressSource;

/**
 * The context available inside an MRTR-capable handler (ADR-0025). MCP 2026-07-28 permits {@code
 * InputRequiredResult} (and, more generally, mid-execution interaction) on exactly three client
 * requests — {@code tools/call}, {@code prompts/get}, {@code resources/read} — and forbids it on
 * any other. {@code MrtrContext} bundles the two things such a handler can do mid-execution —
 * elicit input ({@link McpElicitor}) and report progress ({@link McpProgressSource}) — and exists
 * only for those three handler kinds, making that spec boundary a compile-time fact.
 *
 * <p>Handlers normally declare the leaf type for their kind ({@code McpToolContext}, {@code
 * McpPromptContext}, or {@code McpResourceContext}); each extends this interface.
 */
public interface MrtrContext extends McpElicitor, McpProgressSource {

  /**
   * Returns the name of the handler currently executing (the {@code @McpTool} / {@code @McpPrompt}
   * name, or the resource URI).
   *
   * @return the current handler name
   */
  String handlerName();
}
