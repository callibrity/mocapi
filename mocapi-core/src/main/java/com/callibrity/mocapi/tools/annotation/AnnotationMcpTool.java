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

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.mocapi.tools.McpTool;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public class AnnotationMcpTool implements McpTool {

  // ------------------------------ FIELDS ------------------------------

  private final Tool descriptor;
  private final MethodInvoker<JsonNode> invoker;
  private final boolean streamable;

  // -------------------------- STATIC METHODS --------------------------

  public static List<AnnotationMcpTool> createTools(
      MethodSchemaGenerator generator, MethodInvokerFactory invokerFactory, Object targetObject) {
    return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), ToolMethod.class)
        .stream()
        .map(m -> new AnnotationMcpTool(generator, invokerFactory, targetObject, m))
        .toList();
  }

  // --------------------------- CONSTRUCTORS ---------------------------

  AnnotationMcpTool(
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      Object targetObject,
      Method method) {
    var annotation = method.getAnnotation(ToolMethod.class);
    String name = nameOf(targetObject, method, annotation);
    String title = titleOf(targetObject, method, annotation);
    String description = descriptionOf(targetObject, method, annotation);
    this.invoker = invokerFactory.create(method, targetObject, JsonNode.class);
    ObjectNode inputSchema = generator.generateInputSchema(targetObject, method);

    // Detect McpStreamContext<O> parameter for streaming and output schema
    boolean foundStream = false;
    ObjectNode streamOutputSchema = null;
    for (Parameter param : method.getParameters()) {
      if (McpStreamContext.class.isAssignableFrom(param.getType())) {
        foundStream = true;
        Type genericType = param.getParameterizedType();
        Map<TypeVariable<?>, Type> typeArgs =
            TypeUtils.getTypeArguments(genericType, McpStreamContext.class);
        TypeVariable<?>[] typeParams = McpStreamContext.class.getTypeParameters();
        if (typeArgs != null && !typeArgs.isEmpty()) {
          Type outputType = typeArgs.get(typeParams[0]);
          Class<?> outputClass = TypeUtils.getRawType(outputType, null);
          if (outputClass != null && outputClass != Void.class) {
            streamOutputSchema = generator.generateSchema(outputClass);
          }
        }
        break;
      }
    }
    this.streamable = foundStream;

    ObjectNode outputSchema;
    if (foundStream) {
      outputSchema = streamOutputSchema;
    } else if (isVoid(method)) {
      outputSchema = null;
    } else {
      outputSchema = generator.generateOutputSchema(targetObject, method);
    }
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

  private static boolean isVoid(Method method) {
    return method.getReturnType() == void.class || method.getReturnType() == Void.class;
  }

  // ------------------------ INTERFACE METHODS ------------------------

  // --------------------- Interface McpTool ---------------------

  @Override
  public Tool descriptor() {
    return descriptor;
  }

  @Override
  public boolean isStreamable() {
    return streamable;
  }

  @Override
  public Object call(JsonNode arguments) {
    return invoker.invoke(arguments);
  }
}
