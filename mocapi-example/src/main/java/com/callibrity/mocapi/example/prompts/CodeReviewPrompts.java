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
package com.callibrity.mocapi.example.prompts;

import com.callibrity.mocapi.prompts.GetPromptResult;
import com.callibrity.mocapi.prompts.PromptMessage;
import com.callibrity.mocapi.prompts.Role;
import com.callibrity.mocapi.prompts.annotation.Prompt;
import com.callibrity.mocapi.prompts.annotation.PromptService;
import com.callibrity.mocapi.prompts.content.TextContent;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@PromptService
public class CodeReviewPrompts {

    @Prompt(name = "review-code", description = "Provide a short review of the given code snippet")
    public GetPromptResult reviewCode(@Schema(description = "The programming language used") String language, @Schema(description = "The code snippet to review") String code) {
        var prompt = String.format("""
                Please review the following %s code and suggest improvements:
                
                ```%s
                %s
                ```
                """, language, language, code);

        return new GetPromptResult("Provide a short review of the given code snippet", List.of(
                new PromptMessage(Role.USER, new TextContent(prompt))
        ));
    }
}
