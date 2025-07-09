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
package com.callibrity.mocapi.tools.annotation;

import com.callibrity.mocapi.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.tools.util.HelloTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.github.victools.jsonschema.generator.SchemaVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotationMcpToolTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnnotationMcpToolProviderFactory factory = new DefaultAnnotationMcpToolProviderFactory(mapper, new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7));

    @Test
    void nonCustomizedAnnotationShouldReturnCorrectMetadata() {
        var tools  = factory.create(new HelloTool());
        assertThat(tools.getMcpTools()).hasSize(1);

        var tool = tools.getMcpTools().getFirst();
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("hello-tool.say-hello");
        assertThat(tool.title()).isEqualTo("Hello Tool - Say Hello");
        assertThat(tool.description()).isEqualTo("Hello Tool - Say Hello");
        assertThat(tool.inputSchema().get("type").asText()).isEqualTo("object");
        assertThat(tool.outputSchema().get("type").asText()).isEqualTo("object");
    }

    @Test
    void customizedAnnotationShouldReturnCorrectMetadata() {
        var tools = factory.create(new CustomizedTool());
        assertThat(tools.getMcpTools()).hasSize(1);

        var tool = tools.getMcpTools().getFirst();
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("custom name");
        assertThat(tool.title()).isEqualTo("Custom Title");
        assertThat(tool.description()).isEqualTo("Custom description of a tool");
    }

    @Test
    void shouldCallToolCorrectly() {
        var tool  = factory.create(new HelloTool()).getMcpTools().getFirst();
        var result = tool.call(mapper.createObjectNode().put("name", "Mocapi"));

        assertThat(result).isNotNull();
        assertThat(result.get("message").getNodeType()).isEqualTo(JsonNodeType.STRING);
        assertThat(result.get("message").textValue()).isEqualTo("Hello, Mocapi!");

    }

    @Test
    void invalidReturnTypeShouldThrowException() {
        assertThatThrownBy(() -> factory.create(new InvalidReturnTool()))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}