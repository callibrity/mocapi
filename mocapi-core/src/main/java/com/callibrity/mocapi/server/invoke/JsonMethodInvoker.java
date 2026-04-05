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
package com.callibrity.mocapi.server.invoke;

import com.callibrity.mocapi.server.exception.McpInternalErrorException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class JsonMethodInvoker {

  private final ObjectMapper mapper;
  private final Object target;
  private final Method method;

  public JsonMethodInvoker(ObjectMapper mapper, Object target, Method method) {
    this.mapper = mapper;
    this.target = target;
    this.method = method;
  }

  public JsonNode invoke(ObjectNode arguments) {
    try {
      Object[] args = resolveArguments(arguments);
      Object result = method.invoke(target, args);
      return mapper.valueToTree(result);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw new McpInternalErrorException("Checked exception during method invocation", cause);
    } catch (IllegalAccessException e) {
      throw new McpInternalErrorException("Cannot access method", e);
    }
  }

  private Object[] resolveArguments(ObjectNode arguments) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      String name = parameters[i].getName();
      JsonNode value = arguments.get(name);
      if (value == null || value.isNull()) {
        args[i] = null;
      } else {
        args[i] = mapper.convertValue(value, parameters[i].getType());
      }
    }
    return args;
  }
}
