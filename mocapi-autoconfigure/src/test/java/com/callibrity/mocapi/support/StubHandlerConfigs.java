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
package com.callibrity.mocapi.support;

import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerConfig;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerConfig;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerConfig;
import com.callibrity.mocapi.server.tools.CallToolHandlerConfig;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.ParameterResolver;
import tools.jackson.databind.JsonNode;

/**
 * Reusable stub handler configs for unit tests that drive per-handler customizer beans. Each stub
 * exposes a flattened {@code interceptors} list (all strata concatenated in outer-to-inner order)
 * so existing tests that only care "was something attached?" stay simple.
 */
public final class StubHandlerConfigs {

  private StubHandlerConfigs() {}

  public static Method dummyMethod() {
    try {
      return Object.class.getDeclaredMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  public static final class StubToolConfig implements CallToolHandlerConfig {
    private final Tool descriptor;
    private final Method method;
    public final List<MethodInterceptor<? super JsonNode>> interceptors = new ArrayList<>();
    public final List<Guard> guards = new ArrayList<>();

    public StubToolConfig(Tool descriptor) {
      this(descriptor, dummyMethod());
    }

    public StubToolConfig(Tool descriptor, Method method) {
      this.descriptor = descriptor;
      this.method = method;
    }

    @Override
    public Tool descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public CallToolHandlerConfig correlationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public CallToolHandlerConfig observationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public CallToolHandlerConfig auditInterceptor(MethodInterceptor<? super JsonNode> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public CallToolHandlerConfig validationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public CallToolHandlerConfig invocationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public CallToolHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    @Override
    public CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver) {
      return this;
    }

    private CallToolHandlerConfig addInterceptor(MethodInterceptor<? super JsonNode> interceptor) {
      interceptors.add(interceptor);
      return this;
    }
  }

  public static final class StubPromptConfig implements GetPromptHandlerConfig {
    private final Prompt descriptor;
    private final Method method;
    public final List<MethodInterceptor<? super Map<String, String>>> interceptors =
        new ArrayList<>();
    public final List<Guard> guards = new ArrayList<>();

    public StubPromptConfig(Prompt descriptor) {
      this(descriptor, dummyMethod());
    }

    public StubPromptConfig(Prompt descriptor, Method method) {
      this.descriptor = descriptor;
      this.method = method;
    }

    @Override
    public Prompt descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public GetPromptHandlerConfig correlationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public GetPromptHandlerConfig observationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public GetPromptHandlerConfig auditInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public GetPromptHandlerConfig validationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public GetPromptHandlerConfig invocationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public GetPromptHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    @Override
    public GetPromptHandlerConfig resolver(
        ParameterResolver<? super Map<String, String>> resolver) {
      return this;
    }

    private GetPromptHandlerConfig addInterceptor(
        MethodInterceptor<? super Map<String, String>> i) {
      interceptors.add(i);
      return this;
    }
  }

  public static final class StubResourceConfig implements ReadResourceHandlerConfig {
    private final Resource descriptor;
    private final Method method;
    public final List<MethodInterceptor<? super Object>> interceptors = new ArrayList<>();
    public final List<Guard> guards = new ArrayList<>();

    public StubResourceConfig(Resource descriptor) {
      this(descriptor, dummyMethod());
    }

    public StubResourceConfig(Resource descriptor, Method method) {
      this.descriptor = descriptor;
      this.method = method;
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
      return this;
    }

    @Override
    public ReadResourceHandlerConfig correlationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceHandlerConfig observationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceHandlerConfig auditInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceHandlerConfig validationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceHandlerConfig invocationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig resolver(ParameterResolver<? super Object> resolver) {
      return this;
    }

    private ReadResourceHandlerConfig addInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      interceptors.add(interceptor);
      return this;
    }
  }

  public static final class StubResourceTemplateConfig
      implements ReadResourceTemplateHandlerConfig {
    private final ResourceTemplate descriptor;
    private final Method method;
    public final List<MethodInterceptor<? super Map<String, String>>> interceptors =
        new ArrayList<>();
    public final List<Guard> guards = new ArrayList<>();

    public StubResourceTemplateConfig(ResourceTemplate descriptor) {
      this(descriptor, dummyMethod());
    }

    public StubResourceTemplateConfig(ResourceTemplate descriptor, Method method) {
      this.descriptor = descriptor;
      this.method = method;
    }

    @Override
    public ResourceTemplate descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public ReadResourceTemplateHandlerConfig correlationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceTemplateHandlerConfig observationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceTemplateHandlerConfig auditInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceTemplateHandlerConfig validationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceTemplateHandlerConfig invocationInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      return addInterceptor(interceptor);
    }

    @Override
    public ReadResourceTemplateHandlerConfig guard(Guard guard) {
      guards.add(guard);
      return this;
    }

    @Override
    public ReadResourceTemplateHandlerConfig resolver(
        ParameterResolver<? super Map<String, String>> resolver) {
      return this;
    }

    private ReadResourceTemplateHandlerConfig addInterceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      interceptors.add(interceptor);
      return this;
    }
  }
}
