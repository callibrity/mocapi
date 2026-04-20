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
package com.callibrity.mocapi.server.autoconfigure;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

/**
 * Single-pass scan result: every bean in the context that has one or more {@code @McpTool} /
 * {@code @McpPrompt} / {@code @McpResource} / {@code @McpResourceTemplate} methods, grouped by
 * annotation. Each kind's handler autoconfig consumes its slice via {@link #forAnnotation(Class)}.
 */
public record HandlerMethodsCache(
    Map<Class<? extends Annotation>, List<BeanMethod>> methodsByAnnotation) {

  public HandlerMethodsCache {
    methodsByAnnotation = Map.copyOf(methodsByAnnotation);
  }

  public List<BeanMethod> forAnnotation(Class<? extends Annotation> annotationType) {
    return methodsByAnnotation.getOrDefault(annotationType, List.of());
  }

  public record BeanMethod(String beanName, Object bean, Method method) {}

  /**
   * Walks every bean definition in {@code beanFactory} once, filters to beans whose declared type
   * hosts at least one of the given annotations on any method, then groups each {@code (bean,
   * method)} pair by the annotation it carries. Proxies are unwrapped via {@link
   * AopUtils#getTargetClass(Object)} so annotations placed on the user's declared class are
   * visible. The filter step uses {@link ConfigurableListableBeanFactory#getType(String, boolean)}
   * (with {@code allowFactoryBeanInit=false}) so beans without mocapi methods are never
   * instantiated by the scan itself.
   */
  public static HandlerMethodsCache scan(
      ConfigurableListableBeanFactory beanFactory,
      List<Class<? extends Annotation>> annotationTypes) {
    Map<Class<? extends Annotation>, List<BeanMethod>> map = new LinkedHashMap<>();
    for (String beanName : beanFactory.getBeanNamesForType(Object.class, false, false)) {
      Class<?> declaredType = beanFactory.getType(beanName, false);
      if (declaredType != null
          && hostsAnyAnnotation(ClassUtils.getUserClass(declaredType), annotationTypes)) {
        collectBeanMethods(beanFactory, beanName, annotationTypes, map);
      }
    }
    Map<Class<? extends Annotation>, List<BeanMethod>> sorted = new LinkedHashMap<>();
    map.forEach(
        (annotationType, entries) -> {
          entries.sort(
              Comparator.<BeanMethod, String>comparing(bm -> bm.bean().getClass().getName())
                  .thenComparing(bm -> bm.method().getName()));
          sorted.put(annotationType, List.copyOf(entries));
        });
    return new HandlerMethodsCache(sorted);
  }

  private static void collectBeanMethods(
      ConfigurableListableBeanFactory beanFactory,
      String beanName,
      List<Class<? extends Annotation>> annotationTypes,
      Map<Class<? extends Annotation>, List<BeanMethod>> map) {
    Object bean = beanFactory.getBean(beanName);
    Class<?> targetClass = AopUtils.getTargetClass(bean);
    for (Class<? extends Annotation> annotationType : annotationTypes) {
      for (Method method : MethodUtils.getMethodsListWithAnnotation(targetClass, annotationType)) {
        map.computeIfAbsent(annotationType, k -> new ArrayList<>())
            .add(new BeanMethod(beanName, bean, method));
      }
    }
  }

  private static boolean hostsAnyAnnotation(
      Class<?> type, List<Class<? extends Annotation>> annotationTypes) {
    for (Class<? extends Annotation> annotationType : annotationTypes) {
      if (!MethodUtils.getMethodsListWithAnnotation(type, annotationType).isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
