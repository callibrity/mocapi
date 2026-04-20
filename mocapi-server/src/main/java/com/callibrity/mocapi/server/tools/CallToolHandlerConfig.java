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
import java.lang.reflect.Method;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.ParameterResolver;
import tools.jackson.databind.JsonNode;

/**
 * Per-handler configuration view passed to each {@link CallToolHandlerCustomizer} while a {@link
 * CallToolHandler} is being built. Customizers may inspect the tool descriptor, target method, and
 * target bean, append {@link MethodInterceptor}s to the handler's invocation chain, attach {@link
 * Guard}s that gate visibility and invocation, and register additional {@link ParameterResolver}s
 * that supply values for bespoke parameter types.
 */
public interface CallToolHandlerConfig {

  Tool descriptor();

  Method method();

  Object bean();

  CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor);

  CallToolHandlerConfig guard(Guard guard);

  CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver);
}
