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
package com.callibrity.mocapi.server.resources;

import static com.callibrity.mocapi.server.tools.annotation.Names.humanReadableName;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrDefault;
import static com.callibrity.mocapi.server.util.AnnotationStrings.resolveOrNull;

import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.server.completions.CompletionCandidate;
import com.callibrity.mocapi.server.completions.CompletionCandidates;
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
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/**
 * Pure-Java factory that builds a {@link ReadResourceTemplateHandler} for every
 * {@code @ResourceTemplateMethod}-annotated method on a single {@code @ResourceService} bean.
 */
public final class ReadResourceTemplateHandlers {

  private static final TypeRef<Map<String, String>> VARS_TYPE = new TypeRef<>() {};

  private ReadResourceTemplateHandlers() {}

  /**
   * Walks {@code @ResourceTemplateMethod} methods on {@code resourceServiceBean} and returns one
   * {@link ReadResourceTemplateHandler} per method.
   */
  public static List<ReadResourceTemplateHandler> discover(
      Object resourceServiceBean,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      UnaryOperator<String> valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(
            resourceServiceBean.getClass(), ResourceTemplateMethod.class)
        .stream()
        .sorted(Comparator.comparing(Method::getName))
        .map(method -> build(invokerFactory, resolvers, resourceServiceBean, method, valueResolver))
        .toList();
  }

  private static ReadResourceTemplateHandler build(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      Object targetObject,
      Method method,
      UnaryOperator<String> valueResolver) {
    validateReturnType(targetObject, method);
    ResourceTemplateMethod annotation = method.getAnnotation(ResourceTemplateMethod.class);
    String uriTemplate = valueResolver.apply(annotation.uriTemplate());
    String name =
        resolveOrDefault(
            valueResolver, annotation.name(), () -> humanReadableName(targetObject, method));
    String description = resolveOrDefault(valueResolver, annotation.description(), () -> name);
    String mimeType = resolveOrNull(valueResolver, annotation.mimeType());
    ResourceTemplate descriptor = new ResourceTemplate(uriTemplate, name, description, mimeType);
    MethodInvoker<Map<String, String>> invoker =
        invokerFactory.create(method, targetObject, VARS_TYPE, resolvers);
    return new ReadResourceTemplateHandler(
        descriptor, method, targetObject, invoker, candidatesOf(method));
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

  private static boolean isWholeVarsMap(Parameter parameter) {
    return TypeRef.parameterType(parameter).isAssignableFrom(VARS_TYPE);
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
}
