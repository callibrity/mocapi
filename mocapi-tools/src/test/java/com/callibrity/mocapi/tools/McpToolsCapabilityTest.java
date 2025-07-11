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
package com.callibrity.mocapi.tools;

import com.callibrity.mocapi.tools.annotation.AnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.annotation.DefaultAnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.tools.util.HelloTool;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.SchemaVersion;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class McpToolsCapabilityTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnnotationMcpToolProviderFactory factory = new DefaultAnnotationMcpToolProviderFactory(mapper, new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7));

    @Test
    void shouldListAllTools() {

        var provider  = factory.create(new HelloTool());

        var capability = new McpToolsCapability(List.of(provider));
        var response = capability.listTools("foo-bar");

        assertThat(response).isNotNull();
        assertThat(response.tools()).isNotNull();
        assertThat(response.tools()).hasSize(1);

        var tool = response.tools().getFirst();
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("hello-tool.say-hello");
        assertThat(tool.title()).isEqualTo("Hello Tool - Say Hello");
        assertThat(tool.description()).isEqualTo("Hello Tool - Say Hello");
        assertThat(tool.inputSchema().get("type").asText()).isEqualTo("object");
        assertThat(tool.outputSchema().get("type").asText()).isEqualTo("object");
    }

    @Test
    void shouldDescribeCapabilities() {
        var provider  = factory.create(new HelloTool());

        var capability = new McpToolsCapability(List.of(provider));
        var descriptor = capability.describe();

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.listChanged()).isFalse();
    }

    @Test
    void capabilityNameShouldBeTools() {
        var provider  = factory.create(new HelloTool());

        var capability = new McpToolsCapability(List.of(provider));
        assertEquals("tools", capability.name());
    }

    @Test
    void shouldCallToolSuccessfully() {
        var provider  = factory.create(new HelloTool());

        var capability = new McpToolsCapability(List.of(provider));
        var response = capability.callTool("hello-tool.say-hello", mapper.createObjectNode().put("name", "Mocapi"));

        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, Mocapi!");
    }

    @Test
    void invalidInputShouldThrowException() {
        var provider  = factory.create(new HelloTool());

        var capability = new McpToolsCapability(List.of(provider));
        var request = mapper.createObjectNode();
        assertThatThrownBy(() -> capability.callTool("hello-tool.say-hello", request))
                .isExactlyInstanceOf(JsonRpcInvalidParamsException.class);
    }

    @Test
    void missingToolShouldThrowException() {
        var provider  = factory.create(new HelloTool());

        var capability = new McpToolsCapability(List.of(provider));
        var request = mapper.createObjectNode();
        assertThatThrownBy(() -> capability.callTool("non-existent-tool", request))
                .isExactlyInstanceOf(JsonRpcInvalidParamsException.class)
                .hasMessageContaining("Tool non-existent-tool not found.");
    }

    @Test
    void shouldConvertSimpleObjectNodeToJSONObject() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("stringField", "test")
                .put("intField", 42)
                .put("boolField", true)
                .put("doubleField", 3.14);
        objectNode.putNull("nullField");
        
        var response = capability.callTool("hello-tool.say-hello", 
                mapper.createObjectNode().put("name", "TestUser"));
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, TestUser!");
    }

    @Test
    void shouldConvertNestedObjectNodeToJSONObject() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode nestedObject = mapper.createObjectNode()
                .put("nestedString", "nested value")
                .put("nestedNumber", 123);
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "ComplexUser")
                .set("nested", nestedObject);
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, ComplexUser!");
    }

    @Test
    void shouldConvertArrayNodeToJSONObject() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        var arrayNode = mapper.createArrayNode()
                .add("item1")
                .add("item2")
                .add(42);
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "ArrayUser")
                .set("items", arrayNode);
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, ArrayUser!");
    }

    @Test
    void shouldHandleComplexNestedStructures() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        var innerArray = mapper.createArrayNode()
                .add("arrayItem1")
                .add(999);
        
        var innerObject = mapper.createObjectNode()
                .put("innerProp", "innerValue")
                .set("innerArray", innerArray);
        
        ObjectNode complexObject = mapper.createObjectNode()
                .put("name", "ComplexStructureUser")
                .put("level", 1)
                .set("data", innerObject);
        complexObject.putNull("optionalField");
        
        var response = capability.callTool("hello-tool.say-hello", complexObject);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, ComplexStructureUser!");
    }

    @Test
    void shouldHandleEmptyObjectNode() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode emptyObject = mapper.createObjectNode();
        
        assertThatThrownBy(() -> capability.callTool("hello-tool.say-hello", emptyObject))
                .isExactlyInstanceOf(JsonRpcInvalidParamsException.class);
    }

    @Test
    void shouldHandleNumericTypes() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "NumericUser")
                .put("longField", Long.MAX_VALUE)
                .put("floatField", 2.5f)
                .put("shortField", (short) 100);
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, NumericUser!");
    }

    @Test
    void shouldHandleBigDecimalNodes() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "BigDecimalUser");
        objectNode.put("bigDecimal", new BigDecimal("123.456789012345678901234567890"));
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, BigDecimalUser!");
    }

    @Test
    void shouldHandleBigIntegerNodes() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "BigIntegerUser");
        objectNode.put("bigInteger", new BigInteger("12345678901234567890123456789012345678901234567890"));
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, BigIntegerUser!");
    }

    @Test
    void shouldHandleFloatNodes() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "FloatUser");
        objectNode.put("floatValue", Float.MAX_VALUE);
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, FloatUser!");
    }

    @Test
    void shouldHandleShortNodes() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "ShortUser");
        objectNode.put("shortValue", Short.MAX_VALUE);
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, ShortUser!");
    }

    @Test
    void shouldHandleByteNodes() {
        var provider = factory.create(new HelloTool());
        var capability = new McpToolsCapability(List.of(provider));
        
        ObjectNode objectNode = mapper.createObjectNode()
                .put("name", "ByteUser");
        objectNode.put("byteValue", Byte.MAX_VALUE);
        
        var response = capability.callTool("hello-tool.say-hello", objectNode);
        
        assertThat(response).isNotNull();
        assertThat(response.structuredContent().get("message").textValue()).isEqualTo("Hello, ByteUser!");
    }
}
