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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardEvaluationInterceptor;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.jwcarman.methodical.param.ParameterResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pure-Java factory that builds a single {@link CallToolHandler} from a {@code (bean, @McpTool
 * method)} pair. Bean discovery happens centrally in {@code HandlerMethodsCache}; this helper only
 * constructs the handler.
 */
public final class CallToolHandlers {

  private CallToolHandlers() {}

  /**
   * Builds one {@link CallToolHandler} for the given {@code (bean, method)} pair. The handler's
   * invoker is wired with the structural parameter resolvers ({@link McpToolContextResolver},
   * {@link McpToolParamsResolver}, and a catch-all {@link Jackson3ParameterResolver}) plus any
   * user-supplied resolvers contributed via the customizer chain; customizer-added resolvers are
   * placed ahead of the catch-all so a specific resolver always wins over the generic Jackson
   * fallback. Interceptors contributed by customizers run in order, followed by an {@link
   * InputSchemaValidatingInterceptor} for the compiled input schema (innermost) so schema
   * validation runs closest to the reflective call.
   */
  public static CallToolHandler build(
      Object bean,
      Method method,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      ObjectMapper objectMapper,
      List<CallToolHandlerCustomizer> customizers,
      UnaryOperator<String> valueResolver) {
    validateMcpToolParams(bean, method);
    McpTool annotation = method.getAnnotation(McpTool.class);
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> identifier(bean, method));
    String title =
        resolveOrDefault(valueResolver, annotation.title(), () -> humanReadableName(bean, method));
    String description =
        resolveOrDefault(
            valueResolver, annotation.description(), () -> humanReadableName(bean, method));
    ObjectNode inputSchema = generator.generateInputSchema(bean, method);
    ObjectNode outputSchema = isVoid(method) ? null : generator.generateOutputSchema(bean, method);
    Tool descriptor = new Tool(name, title, description, inputSchema, outputSchema);
    Schema compiledInputSchema = compile(inputSchema);
    MutableConfig config = new MutableConfig(descriptor, method, bean);
    customizers.forEach(c -> c.customize(config));
    List<MethodInterceptor<? super JsonNode>> chain = config.freezeInterceptors();
    List<Guard> guards = config.freezeGuards();
    List<ParameterResolver<? super JsonNode>> resolvers =
        buildResolvers(objectMapper, config.freezeResolvers());
    MethodInvoker<JsonNode> invoker =
        invokerFactory.create(
            method,
            bean,
            JsonNode.class,
            cfg -> {
              resolvers.forEach(cfg::resolver);
              chain.forEach(cfg::interceptor);
              if (!guards.isEmpty()) {
                cfg.interceptor(new GuardEvaluationInterceptor(guards));
              }
              cfg.interceptor(new InputSchemaValidatingInterceptor(compiledInputSchema));
            });
    return new CallToolHandler(descriptor, method, bean, invoker, guards);
  }

  private static List<ParameterResolver<? super JsonNode>> buildResolvers(
      ObjectMapper objectMapper, List<ParameterResolver<? super JsonNode>> userResolvers) {
    List<ParameterResolver<? super JsonNode>> out = new ArrayList<>();
    out.add(new McpToolContextResolver());
    out.add(new McpToolParamsResolver(objectMapper));
    out.addAll(userResolvers);
    out.add(new Jackson3ParameterResolver(objectMapper));
    return List.copyOf(out);
  }

  private static final class MutableConfig implements CallToolHandlerConfig {
    private final Tool descriptor;
    private final Method method;
    private final Object bean;
    private final List<MethodInterceptor<? super JsonNode>> interceptors = new ArrayList<>();
    private final List<Guard> guards = new ArrayList<>();
    private final List<ParameterResolver<? super JsonNode>> resolvers = new ArrayList<>();

    MutableConfig(Tool descriptor, Method method, Object bean) {
      this.descriptor = descriptor;
      this.method = method;
      this.bean = bean;
    }

    @Override
    public Tool descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object bean() {
      return bean;
    }

    @Override
    public CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    @Override
    public CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver) {
      resolvers.add(resolver);
      return this;
    }

    List<MethodInterceptor<? super JsonNode>> freezeInterceptors() {
      return List.copyOf(interceptors);
    }

    List<Guard> freezeGuards() {
      return List.copyOf(guards);
    }

    List<ParameterResolver<? super JsonNode>> freezeResolvers() {
      return List.copyOf(resolvers);
    }
  }

  private static Schema compile(ObjectNode inputSchema) {
    return new SchemaLoader(new JsonParser(inputSchema.toString()).parse()).load();
  }

  private static void validateMcpToolParams(Object bean, Method method) {
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
              + bean.getClass().getName()
              + "."
              + method.getName());
    }
  }

  private static boolean isVoid(Method method) {
    return method.getReturnType() == void.class || method.getReturnType() == Void.class;
  }
}
