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

import com.callibrity.mocapi.api.tools.McpToolParams;
import java.util.Optional;
import org.jwcarman.methodical.ParameterInfo;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.ParameterResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Resolves a tool method's {@code @McpToolParams}-annotated parameter by deserializing the incoming
 * JSON argument tree into the parameter's declared type via the supplied {@link ObjectMapper}.
 */
public class McpToolParamsResolver implements ParameterResolver<JsonNode> {

  private final ObjectMapper mapper;

  public McpToolParamsResolver(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Binding<JsonNode>> bind(ParameterInfo info) {
    if (!info.parameter().isAnnotationPresent(McpToolParams.class)) {
      return Optional.empty();
    }
    ObjectReader reader = mapper.readerFor(info.resolvedType());
    String paramName = info.name();
    return Optional.of(
        arguments -> {
          if (arguments == null || arguments.isNull()) {
            return null;
          }
          try {
            return reader.readValue(mapper.treeAsTokens(arguments));
          } catch (JacksonException e) {
            throw new ParameterResolutionException(
                String.format(
                    "Unable to deserialize @McpToolParams parameter \"%s\": %s",
                    paramName, e.getMessage()),
                e);
          }
        });
  }
}
