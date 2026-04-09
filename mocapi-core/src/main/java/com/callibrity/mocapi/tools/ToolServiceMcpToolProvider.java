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
package com.callibrity.mocapi.tools;

import com.callibrity.mocapi.tools.annotation.AnnotationMcpTool;
import com.callibrity.mocapi.tools.annotation.ToolService;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
@Slf4j
public class ToolServiceMcpToolProvider implements McpToolProvider {

  // ------------------------------ FIELDS ------------------------------

  private final ApplicationContext context;
  private final MethodSchemaGenerator generator;
  private final MethodInvokerFactory invokerFactory;
  private List<AnnotationMcpTool> tools;

  // ------------------------ INTERFACE METHODS ------------------------

  // --------------------- Interface McpToolProvider ---------------------

  @Override
  public List<McpTool> getMcpTools() {
    return List.copyOf(tools);
  }

  // -------------------------- OTHER METHODS --------------------------

  @PostConstruct
  public void initialize() {
    var beans = context.getBeansWithAnnotation(ToolService.class);
    tools =
        beans.entrySet().stream()
            .flatMap(
                entry -> {
                  var beanName = entry.getKey();
                  var bean = entry.getValue();
                  log.info(
                      "Registering MCP tools for @{} bean \"{}\"...",
                      ToolService.class.getSimpleName(),
                      beanName);
                  var list = AnnotationMcpTool.createTools(generator, invokerFactory, bean);
                  list.forEach(tool -> log.info("\tRegistered MCP tool: \"{}\"", tool.name()));
                  return list.stream();
                })
            .toList();
  }
}
