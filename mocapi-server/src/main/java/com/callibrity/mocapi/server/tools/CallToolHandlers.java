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

import static com.callibrity.mocapi.server.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.server.tools.annotation.Names.identifier;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrDefault;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.api.tools.ToolMethod;
import com.callibrity.mocapi.api.tools.ToolService;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringValueResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Static factory that discovers {@code @ToolService} beans in an application context and builds a
 * {@link CallToolHandler} for every {@code @ToolMethod}-annotated method on them.
 */
@Slf4j
public final class CallToolHandlers {

  private CallToolHandlers() {}

  /**
   * Scans every {@link ToolService} bean in the context and returns the concatenated list of
   * handlers. Called once during {@link com.callibrity.mocapi.server.tools.McpToolsService} bean
   * creation.
   */
  public static List<CallToolHandler> discover(
      ApplicationContext context,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      StringValueResolver valueResolver) {
    Map<String, Object> beans = context.getBeansWithAnnotation(ToolService.class);
    return beans.entrySet().stream()
        .flatMap(
            entry -> {
              String beanName = entry.getKey();
              Object bean = entry.getValue();
              log.info(
                  "Registering MCP tools for @{} bean \"{}\"...",
                  ToolService.class.getSimpleName(),
                  beanName);
              List<CallToolHandler> handlers =
                  create(generator, invokerFactory, resolvers, bean, valueResolver);
              handlers.forEach(
                  h -> log.info("\tRegistered MCP tool: \"{}\"", h.descriptor().name()));
              return handlers.stream();
            })
        .toList();
  }

  static List<CallToolHandler> create(
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      Object targetObject,
      StringValueResolver valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), ToolMethod.class)
        .stream()
        .sorted(Comparator.comparing(Method::getName))
        .map(
            method ->
                build(generator, invokerFactory, resolvers, targetObject, method, valueResolver))
        .toList();
  }

  private static CallToolHandler build(
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      Object targetObject,
      Method method,
      StringValueResolver valueResolver) {
    validateMcpToolParams(targetObject, method);
    ToolMethod annotation = method.getAnnotation(ToolMethod.class);
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> identifier(targetObject, method));
    String title =
        resolveOrDefault(
            valueResolver, annotation.title(), () -> humanReadableName(targetObject, method));
    String description =
        resolveOrDefault(
            valueResolver, annotation.description(), () -> humanReadableName(targetObject, method));
    ObjectNode inputSchema = generator.generateInputSchema(targetObject, method);
    ObjectNode outputSchema =
        isVoid(method) ? null : generator.generateOutputSchema(targetObject, method);
    Tool descriptor = new Tool(name, title, description, inputSchema, outputSchema);
    MethodInvoker<JsonNode> invoker =
        invokerFactory.create(method, targetObject, JsonNode.class, resolvers);
    return new CallToolHandler(descriptor, method, targetObject, invoker);
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
}
