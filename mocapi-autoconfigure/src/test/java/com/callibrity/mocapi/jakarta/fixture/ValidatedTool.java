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

import com.callibrity.mocapi.api.tools.McpTool;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Tool fixture carrying jakarta constraints on its argument. Two paths could plausibly catch a
 * violation: mocapi's pre-invocation {@code validateInput} (which runs json-sKema against the
 * tool's generated {@code inputSchema} — and jsonschema-module-jakarta-validation translates
 * {@code @NotBlank}/{@code @Size} into schema constraints automatically) OR methodical's runtime
 * Jakarta validator wrapping the invocation. The integration test pins down which one actually
 * fires in the current mocapi wiring.
 */
public class ValidatedTool {

  @McpTool(name = "shout", description = "Uppercase the input; validated")
  public ShoutResponse shout(@NotBlank @Size(min = 3, max = 40) String message) {
    return new ShoutResponse(message.toUpperCase());
  }

  public record ShoutResponse(String shouted) {}
}
