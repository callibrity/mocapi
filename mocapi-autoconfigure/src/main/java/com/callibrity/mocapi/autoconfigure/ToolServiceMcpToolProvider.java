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
package com.callibrity.mocapi.autoconfigure;

import com.callibrity.mocapi.tools.McpTool;
import com.callibrity.mocapi.tools.McpToolProvider;
import com.callibrity.mocapi.tools.annotation.AnnotationMcpTool;
import com.callibrity.mocapi.tools.annotation.ToolService;
import com.callibrity.mocapi.tools.schema.MethodSchemaGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;

import java.util.List;

@RequiredArgsConstructor
public class ToolServiceMcpToolProvider implements McpToolProvider {

// ------------------------------ FIELDS ------------------------------

    private final ApplicationContext context;
    private final ObjectMapper mapper;
    private final MethodSchemaGenerator generator;
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
        tools = context.getBeansWithAnnotation(ToolService.class).values().stream()
                .flatMap(bean -> AnnotationMcpTool.createTools(mapper, generator, bean).stream())
                .toList();
    }

}
