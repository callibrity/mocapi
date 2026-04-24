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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.handler.HandlerDescriptor;
import com.callibrity.mocapi.server.handler.HandlerKind;
import java.lang.reflect.Method;
import java.util.List;
import org.jwcarman.methodical.MethodInvoker;
import tools.jackson.databind.JsonNode;

/**
 * Server-side handler for a single {@code tools/call} target. Built by {@link CallToolHandlers}
 * from a Spring bean's {@code @McpTool}-annotated method and dispatched to by {@link
 * McpToolsService}.
 */
public final class CallToolHandler {

  private final Tool descriptor;
  private final Method method;
  private final Object bean;
  private final MethodInvoker<JsonNode> invoker;
  private final List<Guard> guards;
  private final ResultMapper resultMapper;

  public CallToolHandler(
      Tool descriptor,
      Method method,
      Object bean,
      MethodInvoker<JsonNode> invoker,
      List<Guard> guards,
      ResultMapper resultMapper) {
    this.descriptor = descriptor;
    this.method = method;
    this.bean = bean;
    this.invoker = invoker;
    this.guards = List.copyOf(guards);
    this.resultMapper = resultMapper;
  }

  public List<Guard> guards() {
    return guards;
  }

  /**
   * Returns the mapper that turns this handler's raw method return value into a {@link
   * com.callibrity.mocapi.model.CallToolResult}. Selected once at handler-build time by {@link
   * ToolReturnTypeClassifier}; callers should not do their own runtime type inspection.
   */
  public ResultMapper resultMapper() {
    return resultMapper;
  }

  public Tool descriptor() {
    return descriptor;
  }

  public String name() {
    return descriptor.name();
  }

  public Method method() {
    return method;
  }

  public Object bean() {
    return bean;
  }

  /**
   * Dispatches the call. Returns the raw method result; conversion to {@link
   * com.callibrity.mocapi.model.CallToolResult} stays in {@link McpToolsService}.
   */
  public Object call(JsonNode arguments) {
    return invoker.invoke(arguments);
  }

  public HandlerDescriptor describe() {
    MethodInvoker.Descriptor d = invoker.describe();
    return new HandlerDescriptor(
        HandlerKind.TOOL, d.declaringClassName(), d.methodName(), d.interceptors());
  }
}
