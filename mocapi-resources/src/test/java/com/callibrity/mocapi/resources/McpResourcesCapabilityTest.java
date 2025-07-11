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
package com.callibrity.mocapi.resources;

import com.callibrity.mocapi.resources.annotation.AnnotationMcpResourceProviderFactory;
import com.callibrity.mocapi.resources.annotation.DefaultAnnotationMcpResourceProviderFactory;
import com.callibrity.mocapi.resources.content.TextResourceContents;
import com.callibrity.mocapi.resources.util.HelloResource;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpResourcesCapabilityTest {

    private final AnnotationMcpResourceProviderFactory factory = new DefaultAnnotationMcpResourceProviderFactory();

    @Test
    void shouldListAllResources() {
        var provider = factory.create(new HelloResource());
        var capability = new McpResourcesCapability(List.of(provider));

        var response = capability.listResources(null);

        assertThat(response.resources()).hasSize(1);
        var resource = response.resources().get(0);
        assertThat(resource.uri()).isEqualTo("hello://greeting");
        assertThat(resource.name()).isEqualTo("Hello Greeting");
        assertThat(resource.title()).isEqualTo("Hello Greeting Resource");
        assertThat(resource.description()).isEqualTo("A simple greeting resource");
        assertThat(resource.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldReadResource() {
        var provider = factory.create(new HelloResource());
        var capability = new McpResourcesCapability(List.of(provider));

        var result = capability.readResource("hello://greeting");

        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEqualTo("Hello from Mocapi Resources!");
        assertThat(textContent.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldThrowExceptionForUnknownResource() {
        var provider = factory.create(new HelloResource());
        var capability = new McpResourcesCapability(List.of(provider));

        assertThatThrownBy(() -> capability.readResource("unknown://resource"))
                .isInstanceOf(JsonRpcInvalidParamsException.class)
                .hasMessage("Resource unknown://resource not found.");
    }

    @Test
    void shouldReturnCapabilityName() {
        var capability = new McpResourcesCapability(List.of());
        assertThat(capability.name()).isEqualTo("resources");
    }

    @Test
    void shouldReturnCapabilityDescriptor() {
        var capability = new McpResourcesCapability(List.of());
        var descriptor = capability.describe();
        assertThat(descriptor.listChanged()).isFalse();
    }
}
