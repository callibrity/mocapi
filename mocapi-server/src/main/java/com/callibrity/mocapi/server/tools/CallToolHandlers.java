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
import com.callibrity.mocapi.server.handler.MutableHandlerState;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.ParameterResolver;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
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
   * validation runs closest to the reflective call. When {@code validateOutput} is {@code true} and
   * the tool declares an output schema, an {@link OutputSchemaValidatingInterceptor} is installed
   * adjacent to the input validator to enforce that the tool's return value matches its declared
   * output schema.
   *
   * <p>{@link ToolReturnTypeClassifier} inspects the method's declared return type up front and
   * picks a single {@link ResultMapper} for the handler; tools whose return type isn't one of the
   * three permitted shapes (void, {@link com.callibrity.mocapi.model.CallToolResult}, or an
   * object-schema POJO) fail to register here with a clear message. When the declared return type
   * is a {@link java.util.concurrent.CompletionStage}, a {@link CompletionStageAwaitingInterceptor}
   * is installed as the innermost interceptor so every other stratum — including output-schema
   * validation — sees the unwrapped value rather than the future.
   */
  public static CallToolHandler build(
      Object bean,
      Method method,
      MethodSchemaGenerator generator,
      ObjectMapper objectMapper,
      List<CallToolHandlerCustomizer> customizers,
      UnaryOperator<String> valueResolver,
      boolean validateOutput) {
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
    ToolReturnTypeClassifier.Classification classification =
        ToolReturnTypeClassifier.classify(bean, method, generator, objectMapper);
    Tool descriptor =
        new Tool(name, title, description, inputSchema, classification.outputSchema());
    Schema compiledInputSchema = compile(inputSchema);
    MutableConfig config = new MutableConfig(descriptor, method, bean);
    customizers.forEach(c -> c.customize(config));
    MutableHandlerState<JsonNode> state = config.state;
    List<ParameterResolver<? super JsonNode>> resolvers =
        buildResolvers(objectMapper, state.resolvers);
    MethodInvoker.Builder<JsonNode> builder = MethodInvoker.builder(method, bean, JsonNode.class);
    resolvers.forEach(builder::resolver);
    // Assemble the interceptor chain outer → inner by stratum.
    state.correlation.forEach(builder::interceptor);
    state.observation.forEach(builder::interceptor);
    state.audit.forEach(builder::interceptor);
    if (!state.guards.isEmpty()) {
      builder.interceptor(new GuardEvaluationInterceptor(state.guards));
    }
    builder.interceptor(new InputSchemaValidatingInterceptor(compiledInputSchema));
    if (validateOutput && classification.outputSchema() != null) {
      builder.interceptor(
          new OutputSchemaValidatingInterceptor(
              compile(classification.outputSchema()), objectMapper));
    }
    state.validation.forEach(builder::interceptor);
    state.invocation.forEach(builder::interceptor);
    if (classification.async()) {
      // Innermost: runs first on the way out of the reflective call, so every outer interceptor
      // (output validator, validation/invocation customizer strata, etc.) sees the awaited value
      // rather than the CompletionStage itself.
      builder.interceptor(new CompletionStageAwaitingInterceptor());
    }
    MethodInvoker<JsonNode> invoker = builder.build();
    return new CallToolHandler(
        descriptor, method, bean, invoker, List.copyOf(state.guards), classification.mapper());
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
    final MutableHandlerState<JsonNode> state = new MutableHandlerState<>();

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
    public CallToolHandlerConfig correlationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      state.correlation.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig observationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      state.observation.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig auditInterceptor(MethodInterceptor<? super JsonNode> interceptor) {
      state.audit.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig validationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      state.validation.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig invocationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      state.invocation.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig guard(Guard guard) {
      state.guards.add(guard);
      return this;
    }

    @Override
    public CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver) {
      state.resolvers.add(resolver);
      return this;
    }
  }

  private static Schema compile(ObjectNode schema) {
    return new SchemaLoader(new JsonParser(schema.toString()).parse()).load();
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
}
