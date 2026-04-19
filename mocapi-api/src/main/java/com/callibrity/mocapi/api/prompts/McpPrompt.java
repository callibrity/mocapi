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
package com.callibrity.mocapi.api.prompts;

import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import java.util.Map;

/**
 * Runtime representation of a single MCP prompt — the {@link #descriptor() descriptor} is what
 * clients see in {@code prompts/list} (name, description, argument specs), and {@link #get(Map)} is
 * what runs when the client sends {@code prompts/get}. Registered prompts are discovered at startup
 * via {@link McpPromptProvider}.
 *
 * <p>Most applications declare prompts with {@code @PromptService} + {@code @PromptMethod} on
 * Spring beans and never implement this interface directly — the annotation processor generates an
 * {@code McpPrompt} per annotated method. Implement this SPI only when you need fully programmatic
 * control.
 */
public interface McpPrompt {

  /** The descriptor advertised to clients in {@code prompts/list}. */
  Prompt descriptor();

  /**
   * Resolve the prompt with the given arguments. Arguments have already been validated against
   * {@link Prompt#arguments()} before this method is invoked.
   */
  GetPromptResult get(Map<String, String> arguments);
}
