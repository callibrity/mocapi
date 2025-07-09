package com.callibrity.mocapi.tools;

import com.callibrity.mocapi.tools.annotation.AnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.annotation.DefaultAnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.tools.util.HelloTool;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.SchemaVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

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
}