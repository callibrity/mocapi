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
package com.callibrity.mocapi.server.tools.annotation;

import static com.callibrity.mocapi.server.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.server.tools.annotation.Names.identifier;
import static java.util.Optional.ofNullable;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.api.tools.ToolMethod;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public class AnnotationMcpTool implements McpTool {

  private final Tool descriptor;
  private final MethodInvoker<JsonNode> invoker;

  public static List<AnnotationMcpTool> createTools(
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      Object targetObject) {
    return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), ToolMethod.class)
        .stream()
        .map(m -> new AnnotationMcpTool(generator, invokerFactory, resolvers, targetObject, m))
        .toList();
  }

  AnnotationMcpTool(
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      Object targetObject,
      Method method) {
    validateMcpToolParams(targetObject, method);
    var annotation = method.getAnnotation(ToolMethod.class);
    String name = nameOf(targetObject, method, annotation);
    String title = titleOf(targetObject, method, annotation);
    String description = descriptionOf(targetObject, method, annotation);
    this.invoker = invokerFactory.create(method, targetObject, JsonNode.class, resolvers);
    ObjectNode inputSchema = generator.generateInputSchema(targetObject, method);
    ObjectNode outputSchema =
        isVoid(method) ? null : generator.generateOutputSchema(targetObject, method);
    this.descriptor = new Tool(name, title, description, inputSchema, outputSchema);
  }

  private static String nameOf(Object targetObject, Method method, ToolMethod annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.name()))
        .orElseGet(() -> identifier(targetObject, method));
  }

  private static String titleOf(Object targetObject, Method method, ToolMethod annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.title()))
        .orElseGet(() -> humanReadableName(targetObject, method));
  }

  private static String descriptionOf(Object targetObject, Method method, ToolMethod annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.description()))
        .orElseGet(() -> humanReadableName(targetObject, method));
  }

  private static void validateMcpToolParams(Object targetObject, Method method) {
    boolean hasMcpToolParams = false;
    int nonContextParamCount = 0;
    for (Parameter param : method.getParameters()) {
      if (param.isAnnotationPresent(McpToolParams.class)) {
        hasMcpToolParams = true;
      } else if (!McpToolContext.class.isAssignableFrom(param.getType())) {
        nonContextParamCount++;
      }
    }
    if (hasMcpToolParams && nonContextParamCount > 0) {
      throw new IllegalArgumentException(
          "@McpToolParams must be the only non-context parameter on the tool method "
              + targetObject.getClass().getName()
              + "."
              + method.getName());
    }
  }

  private static boolean isVoid(Method method) {
    return method.getReturnType() == void.class || method.getReturnType() == Void.class;
  }

  @Override
  public Tool descriptor() {
    return descriptor;
  }

  @Override
  public Object call(JsonNode arguments) {
    return invoker.invoke(arguments);
  }
}
