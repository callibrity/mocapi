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
package com.callibrity.mocapi.tools.annotation;

import static com.callibrity.mocapi.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.tools.annotation.Names.identifier;
import static java.util.Optional.ofNullable;

import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.mocapi.tools.McpTool;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class AnnotationMcpTool implements McpTool {

  // ------------------------------ FIELDS ------------------------------

  private final String name;
  private final String title;
  private final String description;
  private final MethodInvoker<JsonNode> invoker;
  private final ObjectMapper mapper;
  private final ObjectNode inputSchema;
  private final ObjectNode outputSchema;
  private final boolean streamable;

  // -------------------------- STATIC METHODS --------------------------

  public static List<AnnotationMcpTool> createTools(
      ObjectMapper mapper,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      Object targetObject) {
    return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), Tool.class).stream()
        .map(m -> new AnnotationMcpTool(mapper, generator, invokerFactory, targetObject, m))
        .toList();
  }

  // --------------------------- CONSTRUCTORS ---------------------------

  AnnotationMcpTool(
      ObjectMapper mapper,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      Object targetObject,
      Method method) {
    var annotation = method.getAnnotation(Tool.class);
    this.name = nameOf(targetObject, method, annotation);
    this.title = titleOf(targetObject, method, annotation);
    this.description = descriptionOf(targetObject, method, annotation);
    this.streamable = hasStreamContextParam(method);
    this.mapper = mapper;
    this.invoker = invokerFactory.create(method, targetObject, JsonNode.class);
    this.inputSchema = generator.generateInputSchema(targetObject, method);
    this.outputSchema = generator.generateOutputSchema(targetObject, method);
    var outputSchemaType = outputSchema.get("type").asString();
    if (!"object".equals(outputSchemaType)) {
      throw new IllegalArgumentException(
          String.format(
              "MCP tool \"%s\" returns JSON schema type \"%s\" (object is required).",
              name, outputSchemaType));
    }
  }

  private static String nameOf(Object targetObject, Method method, Tool annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.name()))
        .orElseGet(() -> identifier(targetObject, method));
  }

  private static String titleOf(Object targetObject, Method method, Tool annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.title()))
        .orElseGet(() -> humanReadableName(targetObject, method));
  }

  private static String descriptionOf(Object targetObject, Method method, Tool annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.description()))
        .orElseGet(() -> humanReadableName(targetObject, method));
  }

  private static boolean hasStreamContextParam(Method method) {
    return Arrays.stream(method.getParameterTypes())
        .anyMatch(McpStreamContext.class::isAssignableFrom);
  }

  // ------------------------ INTERFACE METHODS ------------------------

  // --------------------- Interface McpTool ---------------------

  @Override
  public String name() {
    return name;
  }

  @Override
  public String title() {
    return title;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public ObjectNode inputSchema() {
    return inputSchema;
  }

  @Override
  public ObjectNode outputSchema() {
    return outputSchema;
  }

  @Override
  public boolean isStreamable() {
    return streamable;
  }

  @Override
  public ObjectNode call(ObjectNode parameters) {
    var rawResult = invoker.invoke(parameters);
    JsonNode result = mapper.valueToTree(rawResult);
    if (result.isObject()) {
      return (ObjectNode) result;
    }
    throw new JsonRpcException(
        JsonRpcProtocol.INTERNAL_ERROR,
        String.format("McpTool %s returned non-object (%s) result.", name, result.getNodeType()));
  }
}
