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
package com.callibrity.mocapi.security.spring;

import com.callibrity.mocapi.server.guards.Guard;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

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
   * untouched.
   */
  public static <C> void attach(C config, Method method, BiConsumer<C, Guard> applier) {
    RequiresScope scopeAnn = method.getAnnotation(RequiresScope.class);
    if (scopeAnn != null) {
      applier.accept(config, new ScopeGuard(scopeAnn.value()));
    }
    RequiresRole roleAnn = method.getAnnotation(RequiresRole.class);
    if (roleAnn != null) {
      applier.accept(config, new RoleGuard(roleAnn.value()));
    }
  }
}
