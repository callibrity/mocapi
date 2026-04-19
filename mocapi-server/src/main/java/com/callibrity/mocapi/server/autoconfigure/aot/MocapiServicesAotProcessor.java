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
package com.callibrity.mocapi.server.autoconfigure.aot;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.tools.McpTool;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Registers AOT reflection hints for every Spring bean that hosts at least one mocapi method-level
 * annotation. For each {@link McpTool}, {@link McpPrompt}, {@link McpResource}, or {@link
 * McpResourceTemplate}-annotated method, registers:
 *
 * <ul>
 *   <li>an {@link ExecutableMode#INVOKE} hint on the method itself
 *   <li>binding hints on every parameter type (via {@link BindingReflectionHintsRegistrar})
 *   <li>binding hints on the non-void return type
 * </ul>
 *
 * <p>Non-matching beans are ignored. No-op for non-native (JIT) builds.
 */
public class MocapiServicesAotProcessor implements BeanRegistrationAotProcessor {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  private static final List<Class<? extends Annotation>> METHOD_ANNOTATIONS =
      List.of(McpTool.class, McpPrompt.class, McpResource.class, McpResourceTemplate.class);

  @Override
  public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
    Class<?> beanClass = registeredBean.getBeanClass();
    if (!hostsAnyMocapiMethod(beanClass)) {
      return null;
    }
    return (generationContext, beanRegistrationCode) -> {
      RuntimeHints hints = generationContext.getRuntimeHints();
      for (Class<? extends Annotation> annotationType : METHOD_ANNOTATIONS) {
        registerAnnotatedMethods(hints, beanClass, annotationType);
      }
    };
  }

  private static boolean hostsAnyMocapiMethod(Class<?> beanClass) {
    for (Method method : beanClass.getDeclaredMethods()) {
      for (Class<? extends Annotation> annotationType : METHOD_ANNOTATIONS) {
        if (method.isAnnotationPresent(annotationType)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void registerAnnotatedMethods(
      RuntimeHints hints, Class<?> beanClass, Class<? extends Annotation> annotationType) {
    for (Method method : beanClass.getDeclaredMethods()) {
      if (!method.isAnnotationPresent(annotationType)) {
        continue;
      }
      hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
      for (Parameter parameter : method.getParameters()) {
        BINDING.registerReflectionHints(hints.reflection(), parameter.getParameterizedType());
      }
      if (method.getReturnType() != void.class && method.getReturnType() != Void.class) {
        BINDING.registerReflectionHints(hints.reflection(), method.getGenericReturnType());
      }
    }
  }
}
