package com.callibrity.mocapi.tools.schema;

import com.callibrity.mocapi.tools.util.HelloTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.SchemaVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMethodSchemaGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testMethodSchemaGeneration() throws Exception {
        DefaultMethodSchemaGenerator generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

        var method = HelloTool.class.getMethod("sayHello", String.class);

        var schema = generator.generateInputSchema(new HelloTool(), method);

        assertThat(schema).isNotNull();
        assertThat(schema.get("properties")).isNotNull();
    }
}