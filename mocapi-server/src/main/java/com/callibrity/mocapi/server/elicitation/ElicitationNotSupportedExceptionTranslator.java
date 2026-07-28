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
package com.callibrity.mocapi.server.elicitation;

import com.callibrity.mocapi.api.elicitation.McpElicitationNotSupportedException;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitationCapability;
import com.callibrity.mocapi.model.MissingRequiredClientCapabilityErrorData;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslator;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Maps {@link McpElicitationNotSupportedException} — a handler elicited but the client did not
 * declare the {@code elicitation} capability — onto the spec's {@code
 * MissingRequiredClientCapabilityError}: JSON-RPC code {@code -32021} with {@code
 * data.requiredCapabilities} naming form-mode elicitation ({@code {"elicitation":{"form":{}}}}).
 * Streamable HTTP additionally surfaces {@code -32021} as {@code 400 Bad Request}.
 */
public class ElicitationNotSupportedExceptionTranslator
    implements JsonRpcExceptionTranslator<McpElicitationNotSupportedException> {

  private final ObjectMapper objectMapper;

  public ElicitationNotSupportedExceptionTranslator(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public JsonRpcErrorDetail translate(McpElicitationNotSupportedException exception) {
    var requiredCapabilities =
        new ClientCapabilities(
            null,
            null,
            null,
            new ElicitationCapability(JsonNodeFactory.instance.objectNode(), null),
            null);
    var data = new MissingRequiredClientCapabilityErrorData(requiredCapabilities);
    return new JsonRpcErrorDetail(
        MissingRequiredClientCapabilityErrorData.CODE,
        exception.getMessage(),
        objectMapper.valueToTree(data));
  }
}
