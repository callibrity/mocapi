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

import com.callibrity.mocapi.resources.content.TextResourceContents;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelloResourceTest {

    private final HelloResource helloResource = new HelloResource();

    @Test
    void shouldReturnGreetingResource() {
        var result = helloResource.getGreeting();
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEqualTo("Hello from Mocapi Resources!");
        assertThat(textContent.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldReturnInfoResource() {
        var result = helloResource.getInfo();
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEqualTo("{\"service\": \"HelloResource\", \"version\": \"1.0\", \"description\": \"Example resource service\"}");
        assertThat(textContent.getMimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldReturnValidJsonInInfoResource() {
        var result = helloResource.getInfo();
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).contains("\"service\"");
        assertThat(textContent.getText()).contains("\"version\"");
        assertThat(textContent.getText()).contains("\"description\"");
        assertThat(textContent.getText()).contains("HelloResource");
        assertThat(textContent.getText()).contains("1.0");
    }

    @Test
    void shouldReturnConsistentGreetingContent() {
        var result1 = helloResource.getGreeting();
        var result2 = helloResource.getGreeting();
        
        var content1 = (TextResourceContents) result1.contents().get(0);
        var content2 = (TextResourceContents) result2.contents().get(0);
        assertThat(content1.getText()).isEqualTo(content2.getText());
        assertThat(content1.getMimeType()).isEqualTo(content2.getMimeType());
    }

    @Test
    void shouldReturnConsistentInfoContent() {
        var result1 = helloResource.getInfo();
        var result2 = helloResource.getInfo();
        
        var content1 = (TextResourceContents) result1.contents().get(0);
        var content2 = (TextResourceContents) result2.contents().get(0);
        assertThat(content1.getText()).isEqualTo(content2.getText());
        assertThat(content1.getMimeType()).isEqualTo(content2.getMimeType());
    }
}
