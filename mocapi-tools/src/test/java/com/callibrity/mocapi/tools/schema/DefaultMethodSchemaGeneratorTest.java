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
package com.callibrity.mocapi.tools.schema;

import com.callibrity.mocapi.tools.annotation.Tool;
import com.callibrity.mocapi.tools.util.HelloResponse;
import com.callibrity.mocapi.tools.util.HelloTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.victools.jsonschema.generator.SchemaVersion;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMethodSchemaGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testMethodSchemaGeneration() throws Exception {
        var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

        var method = HelloTool.class.getMethod("sayHello", String.class);

        var schema = generator.generateInputSchema(new HelloTool(), method);
        System.out.println(schema.toPrettyString());
        assertThat(schema).isNotNull();
        assertThat(schema.get("properties")).isNotNull();
    }

    @Test
    void optionalParametersShouldNotBeRequired() throws Exception {
        var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

        var method = TestTools.class.getMethod("withOptionalParameter", String.class);

        var schema = generator.generateInputSchema(new TestTools(), method);
        assertThat(schema.get("required")).isNull();
    }


    @Test
    void mixedParametersShouldBeMarkedAsRequiredCorrectly() throws Exception {
        var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

        var method = TestTools.class.getMethod("withMixedParameters", String.class, String.class);

        var schema = generator.generateInputSchema(new TestTools(), method);
        assertThat(schema.get("required")).hasSize(1);
        assertThat(schema.get("required")).containsExactly(TextNode.valueOf("name"));
    }

    public static class TestTools {

        @Tool(name="with-optional-parameter")
        public HelloResponse withOptionalParameter(@Nullable String name) {
            return null;
        }


        @Tool(name="with-mixed-parameter")
        public HelloResponse withMixedParameters(String name, @Nullable String optional) {
            return null;
        }

    }
}