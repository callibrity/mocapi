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
package com.callibrity.mocapi.server.prompts;

import static com.callibrity.mocapi.server.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.server.tools.annotation.Names.identifier;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrDefault;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.server.completions.CompletionCandidate;
import com.callibrity.mocapi.server.completions.CompletionCandidates;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardEvaluationInterceptor;
import com.callibrity.mocapi.server.handler.MutableHandlerState;
import com.callibrity.mocapi.server.tools.schema.Parameters;
import com.callibrity.mocapi.server.util.StringMapArgResolver;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.ParameterResolver;
import org.jwcarman.specular.TypeRef;
import org.springframework.core.convert.ConversionService;

/**
 * Pure-Java factory that builds a single {@link GetPromptHandler} from a {@code (bean, @McpPrompt
 * method)} pair. Bean discovery happens centrally in {@code HandlerMethodsCache}; this helper only
 * constructs the handler.
 */
public final class GetPromptHandlers {

  private static final TypeRef<Map<String, String>> ARGS_TYPE = new TypeRef<>() {};

  private GetPromptHandlers() {}

  /**
   * Builds one {@link GetPromptHandler} for the given {@code (bean, method)} pair. User resolvers
   * contributed via the customizer chain run ahead of the catch-all {@link StringMapArgResolver} so
   * a specific resolver always wins over the string-argument fallback.
   */
  public static GetPromptHandler build(
      Object bean,
      Method method,
      ConversionService conversionService,
      List<GetPromptHandlerCustomizer> customizers,
      UnaryOperator<String> valueResolver) {
    validateReturnType(bean, method);
    McpPrompt annotation = method.getAnnotation(McpPrompt.class);
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> identifier(bean, method));
    String title =
        resolveOrDefault(valueResolver, annotation.title(), () -> humanReadableName(bean, method));
    String description =
        resolveOrDefault(
            valueResolver, annotation.description(), () -> humanReadableName(bean, method));
    Prompt descriptor = new Prompt(name, title, description, null, argumentsOf(method));
    MutableConfig config = new MutableConfig(descriptor, method, bean);
    customizers.forEach(c -> c.customize(config));
    MutableHandlerState<Map<String, String>> state = config.state;
    List<ParameterResolver<? super Map<String, String>>> resolvers =
        buildResolvers(conversionService, state.resolvers);
    MethodInvoker.Builder<Map<String, String>> builder =
        MethodInvoker.builder(method, bean, ARGS_TYPE);
    resolvers.forEach(builder::resolver);
    state.correlation.forEach(builder::interceptor);
    state.observation.forEach(builder::interceptor);
    state.audit.forEach(builder::interceptor);
    if (!state.guards.isEmpty()) {
      builder.interceptor(new GuardEvaluationInterceptor(state.guards));
    }
    state.validation.forEach(builder::interceptor);
    state.invocation.forEach(builder::interceptor);
    MethodInvoker<Map<String, String>> invoker = builder.build();
    return new GetPromptHandler(
        descriptor, method, bean, invoker, candidatesOf(method), List.copyOf(state.guards));
  }

  private static List<ParameterResolver<? super Map<String, String>>> buildResolvers(
      ConversionService conversionService,
      List<ParameterResolver<? super Map<String, String>>> userResolvers) {
    List<ParameterResolver<? super Map<String, String>>> out = new ArrayList<>(userResolvers);
    out.add(new StringMapArgResolver(conversionService));
    return List.copyOf(out);
  }

  private static final class MutableConfig implements GetPromptHandlerConfig {
    private final Prompt descriptor;
    private final Method method;
    private final Object bean;
    final MutableHandlerState<Map<String, String>> state = new MutableHandlerState<>();

    MutableConfig(Prompt descriptor, Method method, Object bean) {
      this.descriptor = descriptor;
      this.method = method;
      this.bean = bean;
    }

    @Override
    public Prompt descriptor() {
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
    public GetPromptHandlerConfig correlationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      state.correlation.add(interceptor);
      return this;
    }

    @Override
    public GetPromptHandlerConfig observationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      state.observation.add(interceptor);
      return this;
    }

    @Override
    public GetPromptHandlerConfig auditInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      state.audit.add(interceptor);
      return this;
    }

    @Override
    public GetPromptHandlerConfig validationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      state.validation.add(interceptor);
      return this;
    }

    @Override
    public GetPromptHandlerConfig invocationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      state.invocation.add(interceptor);
      return this;
    }

    @Override
    public GetPromptHandlerConfig guard(Guard guard) {
      state.guards.add(guard);
      return this;
    }

    @Override
    public GetPromptHandlerConfig resolver(
        ParameterResolver<? super Map<String, String>> resolver) {
      state.resolvers.add(resolver);
      return this;
    }
  }

  private static void validateReturnType(Object bean, Method method) {
    if (!GetPromptResult.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          String.format(
              "@McpPrompt %s.%s must return %s",
              bean.getClass().getName(), method.getName(), GetPromptResult.class.getName()));
    }
  }

  private static List<PromptArgument> argumentsOf(Method method) {
    return Arrays.stream(method.getParameters())
        .filter(p -> !isWholeArgsMap(p))
        .map(GetPromptHandlers::toArgument)
        .toList();
  }

  private static List<CompletionCandidate> candidatesOf(Method method) {
    return Arrays.stream(method.getParameters())
        .filter(p -> !isWholeArgsMap(p))
        .map(
            p -> {
              var values = CompletionCandidates.valuesFor(p);
              return values.isEmpty() ? null : new CompletionCandidate(p.getName(), values);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private static boolean isWholeArgsMap(Parameter parameter) {
    return TypeRef.parameterType(parameter).isAssignableFrom(ARGS_TYPE);
  }

  private static PromptArgument toArgument(Parameter parameter) {
    return new PromptArgument(
        parameter.getName(), Parameters.descriptionOf(parameter), Parameters.isRequired(parameter));
  }
}
