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
package com.callibrity.mocapi.resources.annotation;

import com.callibrity.mocapi.resources.ReadResourceResult;
import com.callibrity.mocapi.resources.util.HelloResource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotationMcpResourceTest {

    @Test
    void shouldCreateResourceFromAnnotation() {
        var resources = AnnotationMcpResource.createResources(new HelloResource());

        assertThat(resources).hasSize(1);
        var resource = resources.get(0);
        assertThat(resource.uri()).isEqualTo("hello://greeting");
        assertThat(resource.name()).isEqualTo("Hello Greeting");
        assertThat(resource.title()).isEqualTo("Hello Greeting Resource");
        assertThat(resource.description()).isEqualTo("A simple greeting resource");
        assertThat(resource.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldReadResourceContent() {
        var resources = AnnotationMcpResource.createResources(new HelloResource());
        var resource = resources.get(0);

        var result = resource.read(null);

        assertThat(result.text()).isEqualTo("Hello from Mocapi Resources!");
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldReadResourceContentWithParameters() {
        var resources = AnnotationMcpResource.createResources(new HelloResource());
        var resource = resources.get(0);

        var result = resource.read(Map.of("param", "value"));

        assertThat(result.text()).isEqualTo("Hello from Mocapi Resources!");
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldCreateResourceWithDefaultValues() {
        var resources = AnnotationMcpResource.createResources(new TestResourceWithDefaults());

        assertThat(resources).hasSize(1);
        var resource = resources.get(0);
        assertThat(resource.uri()).isEqualTo("annotation-mcp-resource-test-.-test-resource-with-defaults.get-default-resource");
        assertThat(resource.name()).isEqualTo("Annotation Mcp Resource Test . Test Resource With Defaults - Get Default Resource");
        assertThat(resource.title()).isEqualTo("Annotation Mcp Resource Test . Test Resource With Defaults - Get Default Resource");
        assertThat(resource.description()).isEqualTo("Annotation Mcp Resource Test . Test Resource With Defaults - Get Default Resource");
        assertThat(resource.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldThrowExceptionForInvalidReturnType() {
        assertThatThrownBy(() -> AnnotationMcpResource.createResources(new InvalidReturnTypeResource()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource method 'invalidMethod' returns String (ReadResourceResult is required)");
    }

    @Test
    void shouldHandleMethodInvocationException() {
        var resources = AnnotationMcpResource.createResources(new ThrowingResource());
        var resource = resources.get(0);

        assertThatThrownBy(() -> resource.read(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error invoking resource method");
    }

    @Test
    void shouldCreateMultipleResources() {
        var resources = AnnotationMcpResource.createResources(new MultipleResourcesClass());

        assertThat(resources).hasSize(2);
        assertThat(resources.get(0).name()).isEqualTo("First Resource");
        assertThat(resources.get(1).name()).isEqualTo("Second Resource");
    }

    static class TestResourceWithDefaults {
        @Resource
        public ReadResourceResult getDefaultResource() {
            return ReadResourceResult.text("default content", "text/plain");
        }
    }

    static class InvalidReturnTypeResource {
        @Resource
        public String invalidMethod() {
            return "invalid";
        }
    }

    static class ThrowingResource {
        @Resource(name = "Throwing Resource")
        public ReadResourceResult throwingMethod() {
            throw new RuntimeException("Test exception");
        }
    }

    static class MultipleResourcesClass {
        @Resource(name = "First Resource")
        public ReadResourceResult firstResource() {
            return ReadResourceResult.text("first", "text/plain");
        }

        @Resource(name = "Second Resource")
        public ReadResourceResult secondResource() {
            return ReadResourceResult.text("second", "text/plain");
        }
    }
}
