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
package com.callibrity.mocapi.stream;

import tools.jackson.databind.JsonNode;

/**
 * Result of a {@code sampling/createMessage} request to the client. Contains the raw JSON response
 * from the LLM.
 */
public record SamplingResult(String role, JsonNode content, String model, String stopReason) {

  /** Extracts the text from the content node, if it is a text content block. */
  public String text() {
    if (content == null) {
      return null;
    }
    JsonNode textNode = content.get("text");
    return textNode != null ? textNode.asString() : null;
  }
}
