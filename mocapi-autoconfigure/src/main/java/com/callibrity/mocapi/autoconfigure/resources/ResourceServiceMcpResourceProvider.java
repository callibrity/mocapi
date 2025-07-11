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
package com.callibrity.mocapi.autoconfigure.resources;

import com.callibrity.mocapi.resources.McpResource;
import com.callibrity.mocapi.resources.McpResourceProvider;
import com.callibrity.mocapi.resources.annotation.AnnotationMcpResource;
import com.callibrity.mocapi.resources.annotation.ResourceService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;

import java.util.List;

@RequiredArgsConstructor
public class ResourceServiceMcpResourceProvider implements McpResourceProvider {

    private final ApplicationContext context;
    private List<AnnotationMcpResource> resources = List.of();

    @Override
    public List<McpResource> getMcpResources() {
        return List.copyOf(resources);
    }

    @PostConstruct
    public void initialize() {
        resources = context.getBeansWithAnnotation(ResourceService.class).values().stream()
                .flatMap(bean -> AnnotationMcpResource.createResources(bean).stream())
                .toList();
    }
}
