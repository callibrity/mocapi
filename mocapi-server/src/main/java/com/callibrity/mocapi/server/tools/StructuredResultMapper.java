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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextContent;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mapper for tools that return structured output. Serializes the return value via Jackson and uses
 * the resulting node as both the {@code structuredContent} field and a stringified {@code text}
 * content block of the {@link CallToolResult}.
 *
 * <p>MCP 2026-07-28 widened {@code structuredContent} from a JSON object to <em>any</em> JSON value
 * (object, array, string, number, boolean, or null), so the serialized node is passed through
 * whatever its shape. The mirrored text block satisfies the spec's backwards-compatibility
 * recommendation that a tool returning structured content also return the serialized JSON as text.
 */
public final class StructuredResultMapper implements ResultMapper {

  private final ObjectMapper objectMapper;

  public StructuredResultMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public CallToolResult map(Object result) {
    if (result == null) {
      // Tool signature committed to a structured payload but handed us null. Rather than
      // fabricate a text block, return an empty content array — honest about "nothing to
      // report" while still satisfying the spec's required-content constraint.
      return new CallToolResult(List.of(), null, null, ResultTypes.COMPLETE);
    }
    JsonNode node = objectMapper.valueToTree(result);
    return new CallToolResult(
        List.of(new TextContent(node.toString(), null)), null, node, ResultTypes.COMPLETE);
  }
}
