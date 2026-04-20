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
package com.callibrity.mocapi.server.guards;

import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;

/**
 * MethodInvoker-chain interceptor that evaluates a handler's guard list at call time and aborts the
 * invocation with a {@code -32003 Forbidden} JSON-RPC error when any guard returns {@link
 * GuardDecision.Deny}. Guards are captured once per handler at build time; no allocation occurs per
 * invocation beyond what {@link Guards#evaluate(List)} performs.
 *
 * <p>Position in the chain is fixed by the handler builder: after customizer-contributed
 * interceptors (MDC, observability, user interceptors) so those run — denials surface as errored
 * observations — and before any kind-specific trailing interceptor (e.g. input-schema validation
 * for tools) so a denied call does not waste cycles validating arguments it will never use.
 */
public final class GuardEvaluationInterceptor implements MethodInterceptor<Object> {

  private final List<Guard> guards;

  public GuardEvaluationInterceptor(List<Guard> guards) {
    this.guards = List.copyOf(guards);
  }

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    GuardDecision decision = Guards.evaluate(guards);
    if (decision instanceof GuardDecision.Deny(String reason)) {
      throw new JsonRpcException(JsonRpcErrorCodes.FORBIDDEN, "Forbidden: " + reason);
    }
    return invocation.proceed();
  }
}
