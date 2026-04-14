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
import static java.util.Optional.ofNullable;

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import java.lang.reflect.Method;
import org.apache.commons.lang3.StringUtils;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;

public class AnnotationMcpResource implements McpResource {

  private final Resource descriptor;
  private final MethodInvoker<Object> invoker;

  AnnotationMcpResource(MethodInvokerFactory invokerFactory, Object targetObject, Method method) {
    validateReturnType(targetObject, method);
    var annotation = method.getAnnotation(ResourceMethod.class);
    String uri = annotation.uri();
    String name = nameOf(targetObject, method, annotation);
    String description = descriptionOf(name, annotation);
    String mimeType = StringUtils.trimToNull(annotation.mimeType());
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

  private static String nameOf(Object targetObject, Method method, ResourceMethod annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.name()))
        .orElseGet(() -> humanReadableName(targetObject, method));
  }

  private static String descriptionOf(String name, ResourceMethod annotation) {
    return ofNullable(StringUtils.trimToNull(annotation.description())).orElse(name);
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
