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

import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.handler.HandlerDescriptor;
import com.callibrity.mocapi.server.handler.HandlerKind;
import java.lang.reflect.Method;
import java.util.List;
import org.jwcarman.methodical.MethodInvoker;

/**
 * Server-side handler for a single {@code resources/read} target with a fixed URI. Built by {@link
 * ReadResourceHandlers} from a Spring bean's {@code @McpResource}-annotated method and dispatched
 * to by {@link McpResourcesService}.
 */
public final class ReadResourceHandler {

  private final Resource descriptor;
  private final Method method;
  private final Object bean;
  private final MethodInvoker<Object> invoker;
  private final List<Guard> guards;

  public ReadResourceHandler(
      Resource descriptor,
      Method method,
      Object bean,
      MethodInvoker<Object> invoker,
      List<Guard> guards) {
    this.descriptor = descriptor;
    this.method = method;
    this.bean = bean;
    this.invoker = invoker;
    this.guards = List.copyOf(guards);
  }

  public List<Guard> guards() {
    return guards;
  }

  public Resource descriptor() {
    return descriptor;
  }

  public String uri() {
    return descriptor.uri();
  }

  public Method method() {
    return method;
  }

  public Object bean() {
    return bean;
  }

  /** Dispatches the {@code resources/read} call. */
  public ReadResourceResult read() {
    return (ReadResourceResult) invoker.invoke(null);
  }

  public HandlerDescriptor describe() {
    MethodInvoker.Descriptor d = invoker.describe();
    return new HandlerDescriptor(
        HandlerKind.RESOURCE, d.declaringClassName(), d.methodName(), d.interceptors());
  }
}
