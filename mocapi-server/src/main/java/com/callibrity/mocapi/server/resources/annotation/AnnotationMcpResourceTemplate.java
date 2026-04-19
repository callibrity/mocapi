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
package com.callibrity.mocapi.server.resources.annotation;

import static com.callibrity.mocapi.server.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrDefault;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrNull;

import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.server.completions.CompletionCandidate;
import com.callibrity.mocapi.server.completions.CompletionCandidates;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;
import org.springframework.util.StringValueResolver;

public class AnnotationMcpResourceTemplate implements McpResourceTemplate {

  private static final TypeRef<Map<String, String>> VARS_TYPE = new TypeRef<>() {};

  private final ResourceTemplate descriptor;
  private final MethodInvoker<Map<String, String>> invoker;
  private final List<CompletionCandidate> candidates;

  public static List<AnnotationMcpResourceTemplate> createTemplates(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      Object targetObject,
      StringValueResolver valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(
            targetObject.getClass(), ResourceTemplateMethod.class)
        .stream()
        .sorted(Comparator.comparing(Method::getName))
        .map(
            m ->
                new AnnotationMcpResourceTemplate(
                    invokerFactory, resolvers, targetObject, m, valueResolver))
        .toList();
  }

  AnnotationMcpResourceTemplate(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      Object targetObject,
      Method method,
      StringValueResolver valueResolver) {
    validateReturnType(targetObject, method);
    var annotation = method.getAnnotation(ResourceTemplateMethod.class);
    String uriTemplate = valueResolver.resolveStringValue(annotation.uriTemplate());
    String name =
        resolveOrDefault(
            valueResolver::resolveStringValue,
            annotation.name(),
            () -> humanReadableName(targetObject, method));
    String description =
        resolveOrDefault(valueResolver::resolveStringValue, annotation.description(), () -> name);
    String mimeType = resolveOrNull(valueResolver::resolveStringValue, annotation.mimeType());
    this.invoker = invokerFactory.create(method, targetObject, VARS_TYPE, resolvers);
    this.descriptor = new ResourceTemplate(uriTemplate, name, description, mimeType);
    this.candidates = candidatesOf(method);
  }

  private static List<CompletionCandidate> candidatesOf(Method method) {
    return Arrays.stream(method.getParameters())
        .filter(p -> !isWholeVarsMap(p))
        .map(
            p -> {
              var values = CompletionCandidates.valuesFor(p);
              return values.isEmpty() ? null : new CompletionCandidate(p.getName(), values);
            })
        .filter(Objects::nonNull)
        .toList();
  }

  private static boolean isWholeVarsMap(java.lang.reflect.Parameter parameter) {
    return TypeRef.parameterType(parameter).isAssignableFrom(VARS_TYPE);
  }

  /**
   * Completion candidates derived from annotated method parameters — one entry per URI template
   * variable that has an enum type or a {@code @Schema(allowableValues=...)} attribute.
   */
  public List<CompletionCandidate> completionCandidates() {
    return candidates;
  }

  private static void validateReturnType(Object targetObject, Method method) {
    if (!ReadResourceResult.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          String.format(
              "@ResourceTemplateMethod %s.%s must return %s",
              targetObject.getClass().getName(),
              method.getName(),
              ReadResourceResult.class.getName()));
    }
  }

  @Override
  public ResourceTemplate descriptor() {
    return descriptor;
  }

  @Override
  public ReadResourceResult read(Map<String, String> pathVariables) {
    return (ReadResourceResult) invoker.invoke(pathVariables == null ? Map.of() : pathVariables);
  }
}
