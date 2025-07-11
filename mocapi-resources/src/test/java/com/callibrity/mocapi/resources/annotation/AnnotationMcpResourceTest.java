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
        var invalidResource = new InvalidReturnTypeResource();
        
        assertThatThrownBy(() -> AnnotationMcpResource.createResources(invalidResource))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource method 'invalidMethod' returns String (ReadResourceResult is required)");
    }

    @Test
    void shouldHandleMethodInvocationException() {
        var resources = AnnotationMcpResource.createResources(new ThrowingResource());
        var resource = resources.get(0);

        assertThatThrownBy(() -> resource.read(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Error invoking resource method");
    }

    @Test
    void shouldCreateMultipleResources() {
        var resources = AnnotationMcpResource.createResources(new MultipleResourcesClass());

        assertThat(resources).hasSize(2);
        assertThat(resources.get(0).name()).isEqualTo("First Resource");
        assertThat(resources.get(1).name()).isEqualTo("Second Resource");
    }

    @Test
    void shouldIgnorePrivateMethods() {
        var resources = AnnotationMcpResource.createResources(new PrivateMethodResource());
        
        assertThat(resources).isEmpty();
    }

    @Test
    void shouldUseAnnotationValuesWhenProvided() {
        var resources = AnnotationMcpResource.createResources(new FullyAnnotatedResource());
        var resource = resources.get(0);

        assertThat(resource.uri()).isEqualTo("custom://uri");
        assertThat(resource.name()).isEqualTo("Custom Name");
        assertThat(resource.title()).isEqualTo("Custom Title");
        assertThat(resource.description()).isEqualTo("Custom Description");
        assertThat(resource.mimeType()).isEqualTo("application/custom");
    }

    @Test
    void shouldHandleEmptyAnnotationValues() {
        var resources = AnnotationMcpResource.createResources(new EmptyAnnotationResource());
        var resource = resources.get(0);

        assertThat(resource.uri()).isEqualTo("annotation-mcp-resource-test-.-empty-annotation-resource.empty-method");
        assertThat(resource.name()).isEqualTo("Annotation Mcp Resource Test . Empty Annotation Resource - Empty Method");
        assertThat(resource.title()).isEqualTo("Annotation Mcp Resource Test . Empty Annotation Resource - Empty Method");
        assertThat(resource.description()).isEqualTo("Annotation Mcp Resource Test . Empty Annotation Resource - Empty Method");
        assertThat(resource.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldHandleWhitespaceOnlyAnnotationValues() {
        var resources = AnnotationMcpResource.createResources(new WhitespaceAnnotationResource());
        var resource = resources.get(0);

        assertThat(resource.uri()).isEqualTo("annotation-mcp-resource-test-.-whitespace-annotation-resource.whitespace-method");
        assertThat(resource.name()).isEqualTo("Annotation Mcp Resource Test . Whitespace Annotation Resource - Whitespace Method");
        assertThat(resource.title()).isEqualTo("Annotation Mcp Resource Test . Whitespace Annotation Resource - Whitespace Method");
        assertThat(resource.description()).isEqualTo("Annotation Mcp Resource Test . Whitespace Annotation Resource - Whitespace Method");
        assertThat(resource.mimeType()).isEqualTo("text/plain");
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

    static class PrivateMethodResource {
        @Resource(name = "Private Resource")
        private ReadResourceResult privateMethod() {
            return ReadResourceResult.text("private", "text/plain");
        }
    }

    static class FullyAnnotatedResource {
        @Resource(
                uri = "custom://uri",
                name = "Custom Name",
                title = "Custom Title",
                description = "Custom Description",
                mimeType = "application/custom"
        )
        public ReadResourceResult fullyAnnotated() {
            return ReadResourceResult.text("custom content", "application/custom");
        }
    }

    static class EmptyAnnotationResource {
        @Resource(uri = "", name = "", title = "", description = "")
        public ReadResourceResult emptyMethod() {
            return ReadResourceResult.text("empty", "text/plain");
        }
    }

    static class WhitespaceAnnotationResource {
        @Resource(uri = "   ", name = "   ", title = "   ", description = "   ")
        public ReadResourceResult whitespaceMethod() {
            return ReadResourceResult.text("whitespace", "text/plain");
        }
    }
}
