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

import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;

public final class ResourceServiceScanner {

  private ResourceServiceScanner() {}

  public static List<AnnotationMcpResource> createResources(
      MethodInvokerFactory invokerFactory, Object targetObject) {
    return MethodUtils.getMethodsListWithAnnotation(targetObject.getClass(), ResourceMethod.class)
        .stream()
        .map(m -> new AnnotationMcpResource(invokerFactory, targetObject, m))
        .toList();
  }

  public static List<AnnotationMcpResourceTemplate> createResourceTemplates(
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      Object targetObject) {
    var list = new ArrayList<AnnotationMcpResourceTemplate>();
    for (var m :
        MethodUtils.getMethodsListWithAnnotation(
            targetObject.getClass(), ResourceTemplateMethod.class)) {
      list.add(new AnnotationMcpResourceTemplate(invokerFactory, resolvers, targetObject, m));
    }
    return List.copyOf(list);
  }
}
