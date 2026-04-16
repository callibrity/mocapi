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

import com.callibrity.mocapi.api.prompts.PromptMethod;
import com.callibrity.mocapi.api.prompts.PromptService;
import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.api.tools.ToolMethod;
import com.callibrity.mocapi.api.tools.ToolService;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Registers AOT reflection hints for every Mocapi annotation-driven service bean:
 *
 * <ul>
 *   <li>{@link ToolService} beans — {@link ToolMethod}-annotated methods get {@link
 *       ExecutableMode#INVOKE} hints and binding hints on every parameter type and non-void return
 *       type.
 *   <li>{@link PromptService} beans — {@link PromptMethod}-annotated methods get the same
 *       treatment. Enum and record parameters picked up by Spring's {@code ConversionService} are
 *       covered via the binding registrar.
 *   <li>{@link ResourceService} beans — both {@link ResourceMethod} and {@link
 *       ResourceTemplateMethod}-annotated methods are processed.
 * </ul>
 *
 * <p>Non-matching beans are ignored. No-op for non-native (JIT) builds.
 */
public class MocapiServicesAotProcessor implements BeanRegistrationAotProcessor {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
    Class<?> beanClass = registeredBean.getBeanClass();
    boolean isToolService = beanClass.isAnnotationPresent(ToolService.class);
    boolean isPromptService = beanClass.isAnnotationPresent(PromptService.class);
    boolean isResourceService = beanClass.isAnnotationPresent(ResourceService.class);
    if (!isToolService && !isPromptService && !isResourceService) {
      return null;
    }
    return (generationContext, beanRegistrationCode) -> {
      RuntimeHints hints = generationContext.getRuntimeHints();
      if (isToolService) {
        registerAnnotatedMethods(hints, beanClass, ToolMethod.class);
      }
      if (isPromptService) {
        registerAnnotatedMethods(hints, beanClass, PromptMethod.class);
      }
      if (isResourceService) {
        registerAnnotatedMethods(hints, beanClass, ResourceMethod.class);
        registerAnnotatedMethods(hints, beanClass, ResourceTemplateMethod.class);
      }
    };
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
