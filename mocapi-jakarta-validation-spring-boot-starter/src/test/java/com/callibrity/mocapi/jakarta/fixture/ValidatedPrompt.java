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
package com.callibrity.mocapi.jakarta.fixture;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Prompt fixture whose single argument carries runtime jakarta validation constraints. Serves the
 * integration tests that verify a {@code prompts/get} call with an invalid {@code text} argument
 * surfaces as a JSON-RPC {@code -32602 Invalid params} error with the per-violation detail
 * populated in the response's {@code data} field.
 */
public class ValidatedPrompt {

  @McpPrompt(name = "echo", description = "Validated echo prompt for integration testing")
  public GetPromptResult echo(@NotBlank @Size(min = 3, max = 80) String text) {
    return new GetPromptResult(
        "echo", List.of(new PromptMessage(Role.USER, new TextContent(text, null))));
  }
}
