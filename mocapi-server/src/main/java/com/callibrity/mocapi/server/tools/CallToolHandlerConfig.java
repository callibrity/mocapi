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
import java.lang.reflect.Method;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import tools.jackson.databind.JsonNode;

/**
 * Per-handler configuration view passed to each {@link CallToolHandlerCustomizer} while a {@link
 * CallToolHandler} is being built. Customizers may inspect the tool descriptor, target method, and
 * target bean and append {@link MethodInterceptor}s to the handler's invocation chain.
 */
public interface CallToolHandlerConfig {

  Tool descriptor();

  Method method();

  Object bean();

  CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor);
}
