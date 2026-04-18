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
package com.callibrity.mocapi.examples.validation;

import com.callibrity.mocapi.api.prompts.PromptMethod;
import com.callibrity.mocapi.api.prompts.PromptService;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Prompt demonstrating Jakarta Bean Validation. Violations on a {@code @PromptMethod} parameter
 * surface as JSON-RPC {@code -32602 Invalid params} with per-violation detail in the response's
 * {@code data} field — spec-idiomatic for prompts (the MCP spec explicitly endorses {@code -32602}
 * for "Missing required arguments").
 *
 * <p>Invoke with a blank or too-short {@code text} to observe the validated error:
 *
 * <pre>{@code
 * POST /mcp
 * {"jsonrpc":"2.0","id":1,"method":"prompts/get",
 *  "params":{"name":"summarize","arguments":{"text":""}}}
 *
 * → 200 OK
 * {"jsonrpc":"2.0","id":1,
 *  "error":{"code":-32602,"message":"Invalid params",
 *    "data":[
 *      {"field":"summarize.text","message":"must not be blank"},
 *      {"field":"summarize.text","message":"size must be between 10 and 2000"}
 *    ]}}
 * }</pre>
 */
@Component
@PromptService
public class SummarizePrompt {

  @PromptMethod(name = "summarize", description = "Summarizes the provided text (10-2000 chars)")
  public GetPromptResult summarize(@NotBlank @Size(min = 10, max = 2000) String text) {
    return new GetPromptResult(
        "summarize",
        List.of(
            new PromptMessage(
                Role.USER,
                new TextContent("Please summarize the following text:\n\n" + text, null))));
  }
}
