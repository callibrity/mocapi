/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.security.spring;

import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.util.AnnotationStrings;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * Static helper used by the per-handler customizer autoconfig to read {@link RequiresScope} and
 * {@link RequiresRole} annotations off a handler method and attach the matching guards to that
 * handler's config. Hosts the logic once so the four handler-kind customizer beans each collapse to
 * a single line.
 */
public final class SpringSecurityGuards {

  private SpringSecurityGuards() {}

  /**
   * Attaches a {@link ScopeGuard} and/or {@link RoleGuard} to {@code config} based on the
   * annotations present on {@code method}. When neither annotation is present the config is left
   * untouched. Each {@code value()} element resolves {@code ${...}} placeholders via {@code
   * resolver} before the guard is constructed, so e.g. {@code @RequiresScope("${app.admin-scope}")}
   * resolves once at handler-build (startup) time.
   */
  public static <C> void attach(
      C config, Method method, UnaryOperator<String> resolver, BiConsumer<C, Guard> applier) {
    RequiresScope scopeAnn = method.getAnnotation(RequiresScope.class);
    if (scopeAnn != null) {
      applier.accept(
          config, new ScopeGuard(resolve(resolver, scopeAnn.value(), method, "@RequiresScope")));
    }
    RequiresRole roleAnn = method.getAnnotation(RequiresRole.class);
    if (roleAnn != null) {
      applier.accept(
          config, new RoleGuard(resolve(resolver, roleAnn.value(), method, "@RequiresRole")));
    }
  }

  private static String[] resolve(
      UnaryOperator<String> resolver, String[] values, Method method, String annotationName) {
    return Arrays.stream(values)
        .map(
            v -> {
              String resolved = AnnotationStrings.resolveOrNull(resolver, v);
              if (resolved == null) {
                throw new IllegalStateException(
                    method.getDeclaringClass().getName()
                        + "#"
                        + method.getName()
                        + ": "
                        + annotationName
                        + " value \""
                        + v
                        + "\" resolved to a blank value");
              }
              return resolved;
            })
        .toArray(String[]::new);
  }
}
