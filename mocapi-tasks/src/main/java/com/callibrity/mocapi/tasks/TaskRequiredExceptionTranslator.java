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
package com.callibrity.mocapi.tasks;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.MissingRequiredClientCapabilityErrorData;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslator;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Maps {@link McpTaskRequiredException} — a handler required task-augmented invocation but the
 * client did not declare the {@code io.modelcontextprotocol/tasks} capability — onto the spec's
 * {@code MissingRequiredClientCapabilityError}: JSON-RPC code {@code -32021} with {@code
 * data.requiredCapabilities} naming the {@code tasks} extension ({@code
 * {"extensions":{"io.modelcontextprotocol/tasks":{}}}}). Streamable HTTP additionally surfaces
 * {@code -32021} as {@code 400 Bad Request}.
 */
public class TaskRequiredExceptionTranslator
    implements JsonRpcExceptionTranslator<McpTaskRequiredException> {

  private final ObjectMapper objectMapper;

  public TaskRequiredExceptionTranslator(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public JsonRpcErrorDetail translate(McpTaskRequiredException exception) {
    var requiredCapabilities =
        new ClientCapabilities(
            null,
            null,
            null,
            null,
            Map.of(TasksExtension.EXTENSION_ID, JsonNodeFactory.instance.objectNode()));
    var data = new MissingRequiredClientCapabilityErrorData(requiredCapabilities);
    return new JsonRpcErrorDetail(
        MissingRequiredClientCapabilityErrorData.CODE,
        exception.getMessage(),
        objectMapper.valueToTree(data));
  }
}
