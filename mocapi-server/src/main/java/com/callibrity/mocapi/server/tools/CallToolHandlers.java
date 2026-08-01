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
package com.callibrity.mocapi.server.tools;

import static com.callibrity.mocapi.server.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.server.tools.annotation.Names.identifier;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrDefault;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ContentBlock;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.elicitation.McpElicitorResolver;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardEvaluationInterceptor;
import com.callibrity.mocapi.server.handler.MutableHandlerState;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.ParameterResolver;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
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
   * The invariant wiring shared by every {@link CallToolHandler} on a server: the schema generator,
   * object mapper, the handler- and descriptor-customizer chains, the annotation value resolver,
   * and whether output-schema validation is enforced. Bundled so {@link #build(Object, Method,
   * BuildParams)} takes a single settings argument that a caller constructs once and reuses across
   * every {@code (bean, method)} pair.
   *
   * @param generator generates input and output schemas from method signatures
   * @param objectMapper the Jackson mapper used for schema mapping and the catch-all resolver
   * @param customizers the {@link CallToolHandlerCustomizer} chain applied to each handler
   * @param descriptorCustomizers the {@link ToolDescriptorCustomizer} chain applied to each tool
   *     descriptor
   * @param valueResolver resolves placeholder values in {@code @McpTool} annotation attributes
   * @param validateOutput when {@code true}, installs an output-schema validator for tools that
   *     declare one
   */
  public record BuildParams(
      MethodSchemaGenerator generator,
      ObjectMapper objectMapper,
      List<CallToolHandlerCustomizer> customizers,
      List<ToolDescriptorCustomizer> descriptorCustomizers,
      UnaryOperator<String> valueResolver,
      boolean validateOutput) {}

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
   * <p>The method's declared return type is classified up front by {@link #findResultType} and the
   * surrounding dispatch ladder; tools whose return type isn't one of the four permitted shapes
   * (void, {@link CallToolResult}, {@link CharSequence}, or a POJO with an object-shaped schema)
   * fail to register here with a clear message. When the declared return type contains any {@link
   * java.util.concurrent.CompletionStage} layer, a single {@link
   * CompletionStageAwaitingInterceptor} is installed as the innermost interceptor so every other
   * stratum — including output-schema validation — sees the unwrapped value rather than a stage.
   */
  public static CallToolHandler build(Object bean, Method method, BuildParams params) {
    MethodSchemaGenerator generator = params.generator();
    ObjectMapper objectMapper = params.objectMapper();
    List<CallToolHandlerCustomizer> customizers = params.customizers();
    List<ToolDescriptorCustomizer> descriptorCustomizers = params.descriptorCustomizers();
    UnaryOperator<String> valueResolver = params.valueResolver();
    boolean validateOutput = params.validateOutput();
    validateMcpToolParams(bean, method);
    McpTool annotation = AnnotatedElementUtils.findMergedAnnotation(method, McpTool.class);
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> identifier(bean, method));
    String title =
        resolveOrDefault(valueResolver, annotation.title(), () -> humanReadableName(bean, method));
    String description =
        resolveOrDefault(
            valueResolver, annotation.description(), () -> humanReadableName(bean, method));
    ObjectNode inputSchema = generator.generateInputSchema(bean, method);

    ResultType resultType = findResultType(bean, method);
    MapperAndSchema mapperAndSchema =
        createResultMapper(resultType, bean, method, generator, objectMapper);
    ResultMapper resultMapper = mapperAndSchema.mapper();
    ObjectNode outputSchema = mapperAndSchema.outputSchema();

    Tool descriptor = new Tool(name, title, description, inputSchema, outputSchema);
    for (ToolDescriptorCustomizer descriptorCustomizer : descriptorCustomizers) {
      descriptor = descriptorCustomizer.customize(method, descriptor);
    }
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
    if (validateOutput && outputSchema != null) {
      builder.interceptor(
          new OutputSchemaValidatingInterceptor(compile(outputSchema), objectMapper));
    }
    state.validation.forEach(builder::interceptor);
    state.invocation.forEach(builder::interceptor);
    if (resultType.async()) {
      // Innermost: a single CompletionStageAwaitingInterceptor loops to peel any depth of
      // nesting, so every outer interceptor (output validator, validation/invocation customizer
      // strata) sees the awaited value rather than a stage.
      builder.interceptor(new CompletionStageAwaitingInterceptor());
    }
    MethodInvoker<JsonNode> invoker = builder.build();
    return new CallToolHandler(
        descriptor, method, bean, invoker, List.copyOf(state.guards), resultMapper);
  }

  /**
   * Picks the {@link ResultMapper} that fits the classified return type and (for the structured
   * branch only) generates and validates the advertised output schema. Returns both pieces; the
   * non-structured branches return a {@code null} schema.
   */
  private static MapperAndSchema createResultMapper(
      ResultType resultType,
      Object bean,
      Method method,
      MethodSchemaGenerator generator,
      ObjectMapper objectMapper) {
    if (resultType.isVoid()) {
      return new MapperAndSchema(VoidResultMapper.INSTANCE, null);
    }
    if (resultType.isCallToolResult()) {
      return new MapperAndSchema(PassthroughResultMapper.INSTANCE, null);
    }
    if (resultType.isCharSequence()) {
      return new MapperAndSchema(TextContentResultMapper.INSTANCE, null);
    }
    if (resultType.isContentBlock()) {
      return new MapperAndSchema(ContentBlockResultMapper.INSTANCE, null);
    }
    if (resultType.rawType() == Optional.class) {
      throw rejectReturnType(
          bean,
          method,
          "return type java.util.Optional has an erased element type, so no structuredContent "
              + "schema can be derived. Return the value directly, or return CallToolResult to "
              + "build the result manually.");
    }
    // MCP 2026-07-28 widened structuredContent from a JSON object to any JSON value, so any
    // non-void/CallToolResult/CharSequence return type is structured. Advertise the derived schema
    // when it carries a concrete type; an untyped empty schema (e.g. Object) conveys nothing, so
    // advertise no outputSchema while still mapping the value into structuredContent.
    ObjectNode outputSchema = generator.generateSchema(resultType.rawType());
    ObjectNode advertised = outputSchema.get("type") == null ? null : outputSchema;
    return new MapperAndSchema(new StructuredResultMapper(objectMapper), advertised);
  }

  private record MapperAndSchema(ResultMapper mapper, ObjectNode outputSchema) {}

  /**
   * Walks a method's declared return type, peeling {@link CompletionStage} layers until a non-stage
   * type is reached. Each {@code CompletionStage} layer must carry a concrete type argument (a
   * {@link Class} or a {@link ParameterizedType}); raw, wildcard, and unresolved type variables are
   * rejected because there's no shape to derive a schema from.
   */
  static ResultType findResultType(Object bean, Method method) {
    Class<?> beanClass = bean.getClass();
    Type type = method.getGenericReturnType();
    Class<?> rawType = TypeUtils.getRawType(type, beanClass);
    boolean async = false;
    while (CompletionStage.class.isAssignableFrom(rawType)) {
      if (!(type instanceof ParameterizedType p)
          || !(p.getActualTypeArguments()[0] instanceof Class<?>
              || p.getActualTypeArguments()[0] instanceof ParameterizedType)) {
        throw rejectReturnType(
            bean,
            method,
            "CompletionStage with no concrete type argument ("
                + type.getTypeName()
                + "). Declare a concrete inner type, e.g. CompletionStage<MyRecord>.");
      }
      type = p.getActualTypeArguments()[0];
      rawType = TypeUtils.getRawType(type, beanClass);
      async = true;
    }
    return new ResultType(rawType, async);
  }

  /**
   * Effective tool return type after stripping any {@link CompletionStage} wrapping. The {@code
   * is*} predicates name the four dispatch dimensions used by {@link #build}; an effective type
   * that matches none of them goes through structured-schema generation.
   */
  record ResultType(Class<?> rawType, boolean async) {

    boolean isVoid() {
      return rawType == void.class || rawType == Void.class;
    }

    boolean isCallToolResult() {
      return rawType == CallToolResult.class;
    }

    boolean isCharSequence() {
      return CharSequence.class.isAssignableFrom(rawType);
    }

    boolean isContentBlock() {
      return ContentBlock.class.isAssignableFrom(rawType);
    }
  }

  private static IllegalArgumentException rejectReturnType(
      Object bean, Method method, String reason) {
    return new IllegalArgumentException(
        "@McpTool " + bean.getClass().getName() + "." + method.getName() + ": " + reason);
  }

  private static List<ParameterResolver<? super JsonNode>> buildResolvers(
      ObjectMapper objectMapper, List<ParameterResolver<? super JsonNode>> userResolvers) {
    List<ParameterResolver<? super JsonNode>> out = new ArrayList<>();
    out.add(new McpToolContextResolver());
    out.add(new McpElicitorResolver());
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

  /**
   * Compiles a framework-generated schema for runtime argument/output validation. Only schemas
   * produced by {@link MethodSchemaGenerator} reach this point — they never contain external {@code
   * $ref}s, so {@code SchemaLoader} never dereferences anything remote (SEP-2106 forbids
   * auto-dereferencing external refs). If a future feature feeds user-supplied raw schemas into
   * compilation, remote ref resolution must be disabled explicitly.
   */
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
