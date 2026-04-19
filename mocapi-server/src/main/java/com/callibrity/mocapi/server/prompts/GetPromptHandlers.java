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
import com.callibrity.mocapi.server.tools.schema.Parameters;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/**
 * Pure-Java factory that builds a single {@link GetPromptHandler} from a {@code (bean, @McpPrompt
 * method)} pair. Bean discovery happens centrally in {@code HandlerMethodsCache}; this helper only
 * constructs the handler.
 */
public final class GetPromptHandlers {

  private static final TypeRef<Map<String, String>> ARGS_TYPE = new TypeRef<>() {};

  private GetPromptHandlers() {}

  /** Builds one {@link GetPromptHandler} for the given {@code (bean, method)} pair. */
  public static GetPromptHandler build(
      Object bean,
      Method method,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
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
    List<MethodInterceptor<? super Map<String, String>>> chain = config.freezeInterceptors();
    List<Guard> guards = config.freezeGuards();
    MethodInvoker<Map<String, String>> invoker =
        invokerFactory.create(
            method,
            bean,
            ARGS_TYPE,
            cfg -> {
              resolvers.forEach(cfg::resolver);
              chain.forEach(cfg::interceptor);
            });
    return new GetPromptHandler(descriptor, method, bean, invoker, candidatesOf(method), guards);
  }

  private static final class MutableConfig implements GetPromptHandlerConfig {
    private final Prompt descriptor;
    private final Method method;
    private final Object bean;
    private final List<MethodInterceptor<? super Map<String, String>>> interceptors =
        new ArrayList<>();
    private final List<Guard> guards = new ArrayList<>();

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
    public GetPromptHandlerConfig interceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public GetPromptHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    List<MethodInterceptor<? super Map<String, String>>> freezeInterceptors() {
      return List.copyOf(interceptors);
    }

    List<Guard> freezeGuards() {
      return List.copyOf(guards);
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
