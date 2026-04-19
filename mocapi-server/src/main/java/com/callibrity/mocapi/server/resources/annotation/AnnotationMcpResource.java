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

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.springframework.util.StringValueResolver;

public class AnnotationMcpResource implements McpResource {

  private final Resource descriptor;
  private final MethodInvoker<Object> invoker;

  public static List<AnnotationMcpResource> createResources(
      MethodInvokerFactory invokerFactory, Object targetObject, StringValueResolver valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), ResourceMethod.class)
        .stream()
        .sorted(Comparator.comparing(Method::getName))
        .map(m -> new AnnotationMcpResource(invokerFactory, targetObject, m, valueResolver))
        .toList();
  }

  AnnotationMcpResource(
      MethodInvokerFactory invokerFactory,
      Object targetObject,
      Method method,
      StringValueResolver valueResolver) {
    validateReturnType(targetObject, method);
    var annotation = method.getAnnotation(ResourceMethod.class);
    String uri = valueResolver.resolveStringValue(annotation.uri());
    String name =
        resolveOrDefault(
            valueResolver::resolveStringValue,
            annotation.name(),
            () -> humanReadableName(targetObject, method));
    String description =
        resolveOrDefault(valueResolver::resolveStringValue, annotation.description(), () -> name);
    String mimeType = resolveOrNull(valueResolver::resolveStringValue, annotation.mimeType());
    this.invoker = invokerFactory.create(method, targetObject, Object.class);
    this.descriptor = new Resource(uri, name, description, mimeType);
  }

  private static void validateReturnType(Object targetObject, Method method) {
    if (!ReadResourceResult.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          String.format(
              "@ResourceMethod %s.%s must return %s",
              targetObject.getClass().getName(),
              method.getName(),
              ReadResourceResult.class.getName()));
    }
  }

  @Override
  public Resource descriptor() {
    return descriptor;
  }

  @Override
  public ReadResourceResult read() {
    return (ReadResourceResult) invoker.invoke(null);
  }
}
