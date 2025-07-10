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
package com.callibrity.mocapi.prompts.annotation;

import com.callibrity.mocapi.prompts.GetPromptResult;
import jakarta.annotation.Nullable;

public class TestPrompts {

    @Prompt
    public GetPromptResult multi(@Nullable String a, String b) {
        return GetPromptResult.text("A test prompt", String.format("Test prompt with a=%s, b=%s", a, b));
    }

    @Prompt
    public GetPromptResult single(String a) {
        return GetPromptResult.text("A test prompt", String.format("Test prompt with a=%s", a));
    }

    @Prompt
    public GetPromptResult none() {
        return GetPromptResult.text("A test prompt", "Test prompt with no arguments");
    }

    @Prompt
    public GetPromptResult badReturn() {
        return null; // This is intentionally returning null to test error handling
    }

    @Prompt
    public GetPromptResult evil() {
        throw new RuntimeException("evil");
    }


}
