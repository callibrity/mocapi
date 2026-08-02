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
package com.callibrity.mocapi.server.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * Assembles a {@link McpDispatchInterceptor} chain around the default MRTR dispatch path. {@link
 * #sort} is called once at service-construction time (via {@code @Order}/{@link
 * org.springframework.core.Ordered}, lower values first); {@link #run} folds the sorted list
 * per-dispatch so the lowest-ordered interceptor is outermost and the supplied {@code terminal}
 * supplier — the existing default dispatch path — is innermost. With an empty interceptor list,
 * {@link #run} degenerates to invoking {@code terminal} directly: the zero-interceptor path is
 * byte-for-byte identical to dispatch without this seam.
 */
public final class DispatchChains {

  private DispatchChains() {}

  /** Sorts interceptors by {@code @Order}/{@link org.springframework.core.Ordered}, ascending. */
  public static <H, P> List<McpDispatchInterceptor<H, P>> sort(
      List<McpDispatchInterceptor<H, P>> interceptors) {
    List<McpDispatchInterceptor<H, P>> sorted = new ArrayList<>(interceptors);
    sorted.sort(AnnotationAwareOrderComparator.INSTANCE);
    return List.copyOf(sorted);
  }

  /**
   * Folds {@code sorted} (already ordered lowest-first, i.e. outermost-first) around {@code
   * terminal} and invokes the resulting chain for one dispatch.
   */
  public static <H, P> Object run(
      List<McpDispatchInterceptor<H, P>> sorted, H handler, P params, Supplier<Object> terminal) {
    Supplier<Object> chain = terminal;
    for (int i = sorted.size() - 1; i >= 0; i--) {
      McpDispatchInterceptor<H, P> interceptor = sorted.get(i);
      Supplier<Object> next = chain;
      chain = () -> interceptor.intercept(handler, params, next);
    }
    return chain.get();
  }
}
