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

import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.server.completions.CompletionCandidate;
import com.callibrity.mocapi.server.completions.CompletionCandidates;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
 * Pure-Java factory that builds a single {@link ReadResourceTemplateHandler} from a {@code
 * (bean, @McpResourceTemplate method)} pair. Bean discovery happens centrally in {@code
 * HandlerMethodsCache}; this helper only constructs the handler.
 */
public final class ReadResourceTemplateHandlers {

  private static final TypeRef<Map<String, String>> VARS_TYPE = new TypeRef<>() {};

  private ReadResourceTemplateHandlers() {}

  /** Builds one {@link ReadResourceTemplateHandler} for the given {@code (bean, method)} pair. */
  public static ReadResourceTemplateHandler build(
      Object bean,
      Method method,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      List<MethodInterceptor<? super Map<String, String>>> interceptors,
      UnaryOperator<String> valueResolver) {
    validateReturnType(bean, method);
    McpResourceTemplate annotation = method.getAnnotation(McpResourceTemplate.class);
    String uriTemplate = valueResolver.apply(annotation.uriTemplate());
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> humanReadableName(bean, method));
    String description = resolveOrDefault(valueResolver, annotation.description(), () -> name);
    String mimeType = resolveOrNull(valueResolver, annotation.mimeType());
    ResourceTemplate descriptor = new ResourceTemplate(uriTemplate, name, description, mimeType);
    MethodInvoker<Map<String, String>> invoker =
        invokerFactory.create(
            method,
            bean,
            VARS_TYPE,
            cfg -> {
              resolvers.forEach(cfg::resolver);
              interceptors.forEach(cfg::interceptor);
            });
    return new ReadResourceTemplateHandler(descriptor, method, bean, invoker, candidatesOf(method));
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

  private static void validateReturnType(Object bean, Method method) {
    if (!ReadResourceResult.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          String.format(
              "@McpResourceTemplate %s.%s must return %s",
              bean.getClass().getName(), method.getName(), ReadResourceResult.class.getName()));
    }
  }
}
