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
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
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

  public static List<AnnotationMcpResourceTemplate> createTemplates(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      Object targetObject,
      StringValueResolver valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(
            targetObject.getClass(), ResourceTemplateMethod.class)
        .stream()
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
            valueResolver, annotation.name(), () -> humanReadableName(targetObject, method));
    String description = resolveOrDefault(valueResolver, annotation.description(), () -> name);
    String mimeType = resolveOrNull(valueResolver, annotation.mimeType());
    this.invoker = invokerFactory.create(method, targetObject, VARS_TYPE, resolvers);
    this.descriptor = new ResourceTemplate(uriTemplate, name, description, mimeType);
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
