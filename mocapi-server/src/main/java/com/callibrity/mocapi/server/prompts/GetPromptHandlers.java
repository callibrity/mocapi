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

import com.callibrity.mocapi.api.prompts.PromptMethod;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.server.completions.CompletionCandidate;
import com.callibrity.mocapi.server.completions.CompletionCandidates;
import com.callibrity.mocapi.server.tools.schema.Parameters;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/**
 * Pure-Java factory that builds a {@link GetPromptHandler} for every {@code @PromptMethod}-
 * annotated method on a single {@code @PromptService} bean.
 */
public final class GetPromptHandlers {

  private static final TypeRef<Map<String, String>> ARGS_TYPE = new TypeRef<>() {};

  private GetPromptHandlers() {}

  /**
   * Walks {@code @PromptMethod} methods on {@code promptServiceBean} and returns one {@link
   * GetPromptHandler} per method.
   */
  public static List<GetPromptHandler> discover(
      Object promptServiceBean,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      List<MethodInterceptor<? super Map<String, String>>> interceptors,
      UnaryOperator<String> valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(
            promptServiceBean.getClass(), PromptMethod.class)
        .stream()
        .sorted(Comparator.comparing(Method::getName))
        .map(
            method ->
                build(
                    invokerFactory,
                    resolvers,
                    interceptors,
                    promptServiceBean,
                    method,
                    valueResolver))
        .toList();
  }

  private static GetPromptHandler build(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      List<MethodInterceptor<? super Map<String, String>>> interceptors,
      Object targetObject,
      Method method,
      UnaryOperator<String> valueResolver) {
    validateReturnType(targetObject, method);
    PromptMethod annotation = method.getAnnotation(PromptMethod.class);
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> identifier(targetObject, method));
    String title =
        resolveOrDefault(
            valueResolver, annotation.title(), () -> humanReadableName(targetObject, method));
    String description =
        resolveOrDefault(
            valueResolver, annotation.description(), () -> humanReadableName(targetObject, method));
    Prompt descriptor = new Prompt(name, title, description, null, argumentsOf(method));
    MethodInvoker<Map<String, String>> invoker =
        invokerFactory.create(
            method,
            targetObject,
            ARGS_TYPE,
            cfg -> {
              resolvers.forEach(cfg::resolver);
              interceptors.forEach(cfg::interceptor);
            });
    return new GetPromptHandler(descriptor, method, targetObject, invoker, candidatesOf(method));
  }

  private static void validateReturnType(Object targetObject, Method method) {
    if (!GetPromptResult.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          String.format(
              "@PromptMethod %s.%s must return %s",
              targetObject.getClass().getName(),
              method.getName(),
              GetPromptResult.class.getName()));
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
