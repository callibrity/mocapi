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
import com.callibrity.mocapi.server.guards.GuardEvaluationInterceptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.ParameterResolver;

/**
 * Pure-Java factory that builds a single {@link ReadResourceHandler} from a {@code
 * (bean, @McpResource method)} pair. Bean discovery happens centrally in {@code
 * HandlerMethodsCache}; this helper only constructs the handler.
 */
public final class ReadResourceHandlers {

  private ReadResourceHandlers() {}

  /**
   * Builds one {@link ReadResourceHandler} for the given {@code (bean, method)} pair. Resource
   * methods have no structural parameter resolvers; any resolvers attached by customizers are the
   * only ones wired into the invoker.
   */
  public static ReadResourceHandler build(
      Object bean,
      Method method,
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
    List<Guard> guards = config.guards();
    List<ParameterResolver<? super Object>> resolvers = config.resolvers();
    MethodInvoker.Builder<Object> builder = MethodInvoker.builder(method, bean, Object.class);
    resolvers.forEach(builder::resolver);
    config.correlation().forEach(builder::interceptor);
    config.observation().forEach(builder::interceptor);
    config.audit().forEach(builder::interceptor);
    if (!guards.isEmpty()) {
      builder.interceptor(new GuardEvaluationInterceptor(guards));
    }
    config.validation().forEach(builder::interceptor);
    config.invocation().forEach(builder::interceptor);
    MethodInvoker<Object> invoker = builder.build();
    return new ReadResourceHandler(descriptor, method, bean, invoker, guards);
  }

  private static final class MutableConfig implements ReadResourceHandlerConfig {
    private final Resource descriptor;
    private final Method method;
    private final Object bean;
    private final List<MethodInterceptor<? super Object>> correlation = new ArrayList<>();
    private final List<MethodInterceptor<? super Object>> observation = new ArrayList<>();
    private final List<MethodInterceptor<? super Object>> audit = new ArrayList<>();
    private final List<MethodInterceptor<? super Object>> validation = new ArrayList<>();
    private final List<MethodInterceptor<? super Object>> invocation = new ArrayList<>();
    private final List<Guard> guards = new ArrayList<>();
    private final List<ParameterResolver<? super Object>> resolvers = new ArrayList<>();

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
    public ReadResourceHandlerConfig correlationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      correlation.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig observationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      observation.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig auditInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      audit.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig validationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      validation.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig invocationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      invocation.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig resolver(ParameterResolver<? super Object> resolver) {
      resolvers.add(resolver);
      return this;
    }

    List<MethodInterceptor<? super Object>> correlation() {
      return correlation;
    }

    List<MethodInterceptor<? super Object>> observation() {
      return observation;
    }

    List<MethodInterceptor<? super Object>> audit() {
      return audit;
    }

    List<MethodInterceptor<? super Object>> validation() {
      return validation;
    }

    List<MethodInterceptor<? super Object>> invocation() {
      return invocation;
    }

    List<Guard> guards() {
      return guards;
    }

    List<ParameterResolver<? super Object>> resolvers() {
      return resolvers;
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
