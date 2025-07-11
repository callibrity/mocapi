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
package com.callibrity.mocapi.example.resources;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelloResourceTest {

    private final HelloResource helloResource = new HelloResource();

    @Test
    void shouldReturnGreetingResource() {
        var result = helloResource.getGreeting();
        
        assertThat(result.text()).isEqualTo("Hello from Mocapi Resources!");
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldReturnInfoResource() {
        var result = helloResource.getInfo();
        
        assertThat(result.text()).isEqualTo("{\"service\": \"HelloResource\", \"version\": \"1.0\", \"description\": \"Example resource service\"}");
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldReturnValidJsonInInfoResource() {
        var result = helloResource.getInfo();
        
        assertThat(result.text()).contains("\"service\"");
        assertThat(result.text()).contains("\"version\"");
        assertThat(result.text()).contains("\"description\"");
        assertThat(result.text()).contains("HelloResource");
        assertThat(result.text()).contains("1.0");
    }

    @Test
    void shouldReturnConsistentGreetingContent() {
        var result1 = helloResource.getGreeting();
        var result2 = helloResource.getGreeting();
        
        assertThat(result1.text()).isEqualTo(result2.text());
        assertThat(result1.mimeType()).isEqualTo(result2.mimeType());
    }

    @Test
    void shouldReturnConsistentInfoContent() {
        var result1 = helloResource.getInfo();
        var result2 = helloResource.getInfo();
        
        assertThat(result1.text()).isEqualTo(result2.text());
        assertThat(result1.mimeType()).isEqualTo(result2.mimeType());
    }
}
