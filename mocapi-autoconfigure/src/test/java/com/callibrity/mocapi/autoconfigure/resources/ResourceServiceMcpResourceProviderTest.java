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

import com.callibrity.mocapi.resources.ReadResourceResult;
import com.callibrity.mocapi.resources.annotation.Resource;
import com.callibrity.mocapi.resources.annotation.ResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceServiceMcpResourceProviderTest {

    @Test
    void shouldDiscoverResourceServiceBeans() {
        var context = new AnnotationConfigApplicationContext();
        context.register(TestResourceService.class);
        context.refresh();

        var provider = new ResourceServiceMcpResourceProvider(context);
        provider.initialize();
        var resources = provider.getMcpResources();

        assertThat(resources).hasSize(2);
        assertThat(resources.get(0).name()).isEqualTo("First Resource");
        assertThat(resources.get(1).name()).isEqualTo("Second Resource");
    }

    @Test
    void shouldReturnEmptyListWhenNoResourceServices() {
        var context = new AnnotationConfigApplicationContext();
        context.register(NonResourceService.class);
        context.refresh();

        var provider = new ResourceServiceMcpResourceProvider(context);
        provider.initialize();
        var resources = provider.getMcpResources();

        assertThat(resources).isEmpty();
    }

    @Test
    void shouldDiscoverMultipleResourceServices() {
        var context = new AnnotationConfigApplicationContext();
        context.register(TestResourceService.class, AnotherResourceService.class);
        context.refresh();

        var provider = new ResourceServiceMcpResourceProvider(context);
        provider.initialize();
        var resources = provider.getMcpResources();

        assertThat(resources).hasSize(3);
        var resourceNames = resources.stream().map(r -> r.name()).toList();
        assertThat(resourceNames).contains("First Resource", "Second Resource", "Third Resource");
    }

    @Test
    void shouldHandleEmptyContext() {
        var context = new AnnotationConfigApplicationContext();
        context.refresh();

        var provider = new ResourceServiceMcpResourceProvider(context);
        provider.initialize();
        var resources = provider.getMcpResources();

        assertThat(resources).isEmpty();
    }

    @Component
    @ResourceService
    static class TestResourceService {
        @Resource(name = "First Resource")
        public ReadResourceResult firstResource() {
            return ReadResourceResult.text("first", "text/plain", "");
        }

        @Resource(name = "Second Resource")
        public ReadResourceResult secondResource() {
            return ReadResourceResult.text("second", "text/plain", "");
        }
    }

    @Component
    @ResourceService
    static class AnotherResourceService {
        @Resource(name = "Third Resource")
        public ReadResourceResult thirdResource() {
            return ReadResourceResult.text("third", "text/plain", "");
        }
    }

    @Component
    static class NonResourceService {
        public ReadResourceResult notAResource() {
            return ReadResourceResult.text("not a resource", "text/plain", "");
        }
    }
}
