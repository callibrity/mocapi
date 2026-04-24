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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.TextContent;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Mapper for tools whose declared return type derives to an object-shaped JSON schema. Serializes
 * the return value via Jackson, uses the resulting {@link ObjectNode} as both the {@code text}
 * content block (stringified) and the {@code structuredContent} field of the {@link
 * CallToolResult}.
 *
 * <p>{@link ToolReturnTypeClassifier} verifies at registration time that the declared return type
 * produces a JSON schema of {@code type: object} with declared properties, so the runtime {@code
 * valueToTree} result should always be an {@link ObjectNode}. If a tool somehow returns a value
 * that serializes to a non-object node (e.g., via a custom Jackson serializer), that's a contract
 * violation and the mapper throws — surfacing the drift rather than silently dropping {@code
 * structuredContent}.
 */
public final class StructuredResultMapper implements ResultMapper {

  private static final String NON_OBJECT_RUNTIME_SHAPE =
      "Tool return value serialized to a %s node, but structuredContent must be a JSON object. "
          + "The declared return type advertised an object-shaped schema; a custom Jackson "
          + "serializer or unexpected subclass likely changed the runtime shape.";

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
      return new CallToolResult(List.of(), null, null);
    }
    JsonNode node = objectMapper.valueToTree(result);
    if (!(node instanceof ObjectNode obj)) {
      throw new IllegalStateException(String.format(NON_OBJECT_RUNTIME_SHAPE, node.getNodeType()));
    }
    return new CallToolResult(List.of(new TextContent(obj.toString(), null)), null, obj);
  }
}
