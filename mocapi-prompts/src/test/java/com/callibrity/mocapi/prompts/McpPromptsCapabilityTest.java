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
package com.callibrity.mocapi.prompts;

import com.callibrity.mocapi.prompts.annotation.AnnotationMcpPrompt;
import com.callibrity.mocapi.prompts.annotation.TestPrompts;
import com.callibrity.mocapi.prompts.content.TextContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class McpPromptsCapabilityTest {

    private McpPromptsCapability capability;

    @BeforeEach
    void setUp() {
        List<AnnotationMcpPrompt> prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());
        final McpPromptProvider provider = () -> List.copyOf(prompts);
        this.capability = new McpPromptsCapability(List.of(provider));
    }

    @Test
    void listPromptsShouldReturnAllPrompts() {
        var response = capability.listPrompts("foo");
        assertThat(response.nextCursor()).isNull();
        assertThat(response.prompts()).hasSize(5);
    }

    @Test
    void nameShouldBePrompts() {
        assertThat(capability.name()).isEqualTo("prompts");
    }

    @Test
    void shouldDescribeCapabilityCorrectly() {
        assertThat(capability.describe()).isEqualTo(new McpPromptsCapability.PromptsCapabilityDescriptor(false));
    }

    @Test
    void shouldReturnResultsFromPrompt() {
        var result = capability.getPrompt("test-prompts.none", new HashMap<>());
        assertThat(result.description()).isEqualTo("A test prompt");
        assertThat(result.messages()).hasSize(1);
        var message = result.messages().getFirst();
        assertThat(message.role()).isEqualTo(Role.USER);
        assertThat(message.content()).isInstanceOf(TextContent.class);
    }
}