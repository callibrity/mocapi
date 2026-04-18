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
package com.callibrity.mocapi.server.prompts.annotation;

import static com.callibrity.mocapi.server.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.server.tools.annotation.Names.identifier;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrDefault;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.prompts.PromptMethod;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.server.tools.schema.Parameters;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;
import org.springframework.util.StringValueResolver;

public class AnnotationMcpPrompt implements McpPrompt {

  private static final TypeRef<Map<String, String>> ARGS_TYPE = new TypeRef<>() {};

  private final Prompt descriptor;
  private final MethodInvoker<Map<String, String>> invoker;

  public static List<AnnotationMcpPrompt> createPrompts(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      Object targetObject,
      StringValueResolver valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), PromptMethod.class)
        .stream()
        .map(
            m -> new AnnotationMcpPrompt(invokerFactory, resolvers, targetObject, m, valueResolver))
        .toList();
  }

  AnnotationMcpPrompt(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      Object targetObject,
      Method method,
      StringValueResolver valueResolver) {
    validateReturnType(targetObject, method);
    var annotation = method.getAnnotation(PromptMethod.class);
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> identifier(targetObject, method));
    String title =
        resolveOrDefault(
            valueResolver, annotation.title(), () -> humanReadableName(targetObject, method));
    String description =
        resolveOrDefault(
            valueResolver, annotation.description(), () -> humanReadableName(targetObject, method));
    this.invoker = invokerFactory.create(method, targetObject, ARGS_TYPE, resolvers);
    this.descriptor = new Prompt(name, title, description, null, argumentsOf(method));
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
        .map(AnnotationMcpPrompt::toArgument)
        .toList();
  }

  private static boolean isWholeArgsMap(Parameter parameter) {
    return TypeRef.parameterType(parameter).isAssignableFrom(ARGS_TYPE);
  }

  private static PromptArgument toArgument(Parameter parameter) {
    return new PromptArgument(
        parameter.getName(), Parameters.descriptionOf(parameter), Parameters.isRequired(parameter));
  }

  @Override
  public Prompt descriptor() {
    return descriptor;
  }

  @Override
  public GetPromptResult get(Map<String, String> arguments) {
    return (GetPromptResult) invoker.invoke(arguments == null ? Map.of() : arguments);
  }
}
