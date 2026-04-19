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

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.server.guards.Guard;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.intercept.MethodInterceptor;

/**
 * Pure-Java factory that builds a single {@link ReadResourceHandler} from a {@code
 * (bean, @McpResource method)} pair. Bean discovery happens centrally in {@code
 * HandlerMethodsCache}; this helper only constructs the handler.
 */
public final class ReadResourceHandlers {

  private ReadResourceHandlers() {}

  /** Builds one {@link ReadResourceHandler} for the given {@code (bean, method)} pair. */
  public static ReadResourceHandler build(
      Object bean,
      Method method,
      MethodInvokerFactory invokerFactory,
      List<ReadResourceHandlerCustomizer> customizers,
      UnaryOperator<String> valueResolver) {
    validateReturnType(bean, method);
    McpResource annotation = method.getAnnotation(McpResource.class);
    String uri = valueResolver.apply(annotation.uri());
    String name =
        resolveOrDefault(valueResolver, annotation.name(), () -> humanReadableName(bean, method));
    String description = resolveOrDefault(valueResolver, annotation.description(), () -> name);
    String mimeType = resolveOrNull(valueResolver, annotation.mimeType());
    Resource descriptor = new Resource(uri, name, description, mimeType);
    MutableConfig config = new MutableConfig(descriptor, method, bean);
    customizers.forEach(c -> c.customize(config));
    List<MethodInterceptor<? super Object>> chain = config.freezeInterceptors();
    List<Guard> guards = config.freezeGuards();
    MethodInvoker<Object> invoker =
        invokerFactory.create(method, bean, Object.class, cfg -> chain.forEach(cfg::interceptor));
    return new ReadResourceHandler(descriptor, method, bean, invoker, guards);
  }

  private static final class MutableConfig implements ReadResourceHandlerConfig {
    private final Resource descriptor;
    private final Method method;
    private final Object bean;
    private final List<MethodInterceptor<? super Object>> interceptors = new ArrayList<>();
    private final List<Guard> guards = new ArrayList<>();

    MutableConfig(Resource descriptor, Method method, Object bean) {
      this.descriptor = descriptor;
      this.method = method;
      this.bean = bean;
    }

    @Override
    public Resource descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object bean() {
      return bean;
    }

    @Override
    public ReadResourceHandlerConfig interceptor(MethodInterceptor<? super Object> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    List<MethodInterceptor<? super Object>> freezeInterceptors() {
      return List.copyOf(interceptors);
    }

    List<Guard> freezeGuards() {
      return List.copyOf(guards);
    }
  }

  private static void validateReturnType(Object bean, Method method) {
    if (!ReadResourceResult.class.isAssignableFrom(method.getReturnType())) {
      throw new IllegalArgumentException(
          String.format(
              "@McpResource %s.%s must return %s",
              bean.getClass().getName(), method.getName(), ReadResourceResult.class.getName()));
    }
  }
}
