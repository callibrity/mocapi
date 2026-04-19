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
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.param.ParameterResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pure-Java factory that builds a {@link CallToolHandler} for every {@code @ToolMethod}-annotated
 * method on a single {@code @ToolService} bean.
 */
public final class CallToolHandlers {

  private CallToolHandlers() {}

  /**
   * Walks {@code @ToolMethod} methods on {@code toolServiceBean} and returns one {@link
   * CallToolHandler} per method. Each handler's invoker is wired with the supplied parameter
   * resolvers plus the supplied interceptor list; an {@link InputSchemaValidatingInterceptor} for
   * that tool's compiled input schema is appended last (innermost) so schema validation runs
   * closest to the reflective call.
   */
  public static List<CallToolHandler> discover(
      Object toolServiceBean,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      List<MethodInterceptor<? super JsonNode>> interceptors,
      UnaryOperator<String> valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(toolServiceBean.getClass(), ToolMethod.class)
        .stream()
        .sorted(Comparator.comparing(Method::getName))
        .map(
            method ->
                build(
                    generator,
                    invokerFactory,
                    resolvers,
                    interceptors,
                    toolServiceBean,
                    method,
                    valueResolver))
        .toList();
  }

  private static CallToolHandler build(
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      List<MethodInterceptor<? super JsonNode>> interceptors,
      Object targetObject,
      Method method,
      UnaryOperator<String> valueResolver) {
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
    Schema compiledInputSchema = compile(inputSchema);
    MethodInvoker<JsonNode> invoker =
        invokerFactory.create(
            method,
            targetObject,
            JsonNode.class,
            cfg -> {
              resolvers.forEach(cfg::resolver);
              interceptors.forEach(cfg::interceptor);
              cfg.interceptor(new InputSchemaValidatingInterceptor(compiledInputSchema));
            });
    return new CallToolHandler(descriptor, method, targetObject, invoker);
  }

  private static Schema compile(ObjectNode inputSchema) {
    return new SchemaLoader(new JsonParser(inputSchema.toString()).parse()).load();
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
