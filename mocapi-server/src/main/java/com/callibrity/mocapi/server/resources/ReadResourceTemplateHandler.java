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
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.server.completions.CompletionCandidate;
import com.callibrity.mocapi.server.guards.Guard;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.jwcarman.methodical.MethodInvoker;

/**
 * Server-side handler for a single {@code resources/read} target with a parameterized URI. Built by
 * {@link ReadResourceTemplateHandlers} from a Spring bean's {@code @McpResourceTemplate}- annotated
 * method and dispatched to by {@link McpResourcesService} after the requested URI is matched
 * against the template.
 */
public final class ReadResourceTemplateHandler {

  private final ResourceTemplate descriptor;
  private final Method method;
  private final Object bean;
  private final MethodInvoker<Map<String, String>> invoker;
  private final List<CompletionCandidate> completionCandidates;
  private final List<Guard> guards;

  public ReadResourceTemplateHandler(
      ResourceTemplate descriptor,
      Method method,
      Object bean,
      MethodInvoker<Map<String, String>> invoker,
      List<CompletionCandidate> completionCandidates,
      List<Guard> guards) {
    this.descriptor = descriptor;
    this.method = method;
    this.bean = bean;
    this.invoker = invoker;
    this.completionCandidates = List.copyOf(completionCandidates);
    this.guards = List.copyOf(guards);
  }

  public List<Guard> guards() {
    return guards;
  }

  public ResourceTemplate descriptor() {
    return descriptor;
  }

  public String uriTemplate() {
    return descriptor.uriTemplate();
  }

  public Method method() {
    return method;
  }

  public Object bean() {
    return bean;
  }

  /**
   * Completion candidates derived from annotated method parameters — one entry per URI-template
   * variable that has an enum type or a {@code @Schema(allowableValues=...)} attribute.
   */
  public List<CompletionCandidate> completionCandidates() {
    return completionCandidates;
  }

  /** Dispatches the {@code resources/read} call for the given path-variable bindings. */
  public ReadResourceResult read(Map<String, String> pathVariables) {
    return (ReadResourceResult) invoker.invoke(pathVariables == null ? Map.of() : pathVariables);
  }
}
