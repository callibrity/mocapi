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
package com.callibrity.mocapi.tools.annotation;

import com.callibrity.mocapi.tools.McpToolProvider;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jwcarman.methodical.MethodInvokerFactory;

@RequiredArgsConstructor
public class DefaultAnnotationMcpToolProviderFactory implements AnnotationMcpToolProviderFactory {

  // ------------------------------ FIELDS ------------------------------

  private final MethodSchemaGenerator generator;
  private final MethodInvokerFactory invokerFactory;

  // ------------------------ INTERFACE METHODS ------------------------

  // --------------------- Interface AnnotationMcpToolProviderFactory ---------------------

  public McpToolProvider create(Object targetObject) {
    final var tools = AnnotationMcpTool.createTools(generator, invokerFactory, targetObject);
    return () -> List.copyOf(tools);
  }
}
