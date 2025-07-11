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

import com.callibrity.mocapi.resources.util.HelloResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
