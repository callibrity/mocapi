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

import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;

/**
 * Pure-Java factory that builds a {@link ReadResourceHandler} for every {@code @ResourceMethod}-
 * annotated method on a single {@code @ResourceService} bean.
 */
public final class ReadResourceHandlers {

  private ReadResourceHandlers() {}

  /**
   * Walks {@code @ResourceMethod} methods on {@code resourceServiceBean} and returns one {@link
   * ReadResourceHandler} per method.
   */
  public static List<ReadResourceHandler> discover(
      Object resourceServiceBean,
      MethodInvokerFactory invokerFactory,
      UnaryOperator<String> valueResolver) {
    return MethodUtils.getMethodsListWithAnnotation(
            resourceServiceBean.getClass(), ResourceMethod.class)
        .stream()
        .sorted(Comparator.comparing(Method::getName))
        .map(method -> build(invokerFactory, resourceServiceBean, method, valueResolver))
        .toList();
  }

  private static ReadResourceHandler build(
      MethodInvokerFactory invokerFactory,
      Object targetObject,
      Method method,
      UnaryOperator<String> valueResolver) {
    validateReturnType(targetObject, method);
    ResourceMethod annotation = method.getAnnotation(ResourceMethod.class);
    String uri = valueResolver.apply(annotation.uri());
    String name =
        resolveOrDefault(
            valueResolver, annotation.name(), () -> humanReadableName(targetObject, method));
    String description = resolveOrDefault(valueResolver, annotation.description(), () -> name);
    String mimeType = resolveOrNull(valueResolver, annotation.mimeType());
    Resource descriptor = new Resource(uri, name, description, mimeType);
    MethodInvoker<Object> invoker = invokerFactory.create(method, targetObject, Object.class);
    return new ReadResourceHandler(descriptor, method, targetObject, invoker);
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
}
