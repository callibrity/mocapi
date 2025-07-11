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
package com.callibrity.mocapi.autoconfigure.prompts;

import com.callibrity.mocapi.prompts.McpPrompt;
import com.callibrity.mocapi.prompts.McpPromptProvider;
import com.callibrity.mocapi.prompts.annotation.AnnotationMcpPrompt;
import com.callibrity.mocapi.prompts.annotation.PromptService;
import com.callibrity.ripcurl.core.annotation.JsonRpcService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class PromptServiceMcpPromptProvider implements McpPromptProvider {

// ------------------------------ FIELDS ------------------------------

    private final ApplicationContext context;
    private List<AnnotationMcpPrompt> prompts;

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface McpPromptProvider ---------------------

    @Override
    public List<McpPrompt> getMcpPrompts() {
        return List.copyOf(prompts);
    }

// -------------------------- OTHER METHODS --------------------------

    @PostConstruct
    public void initialize() {
        this.prompts = context.getBeansWithAnnotation(PromptService.class).entrySet().stream()
                .flatMap(entry -> {
                    var beanName = entry.getKey();
                    var bean = entry.getValue();
                    log.info("Registering MCP prompts for @{} bean \"{}\"...", PromptService.class.getSimpleName(), beanName);
                    var list = AnnotationMcpPrompt.createPrompts(bean);
                    list.forEach(prompt -> log.info("\tRegistered MCP prompt: \"{}\".", prompt.name()));
                    return list.stream();
                })
                .flatMap(targetObject -> AnnotationMcpPrompt.createPrompts(targetObject).stream())
                .toList();
    }

}
