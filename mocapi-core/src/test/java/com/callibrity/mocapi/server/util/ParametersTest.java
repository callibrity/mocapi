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
package com.callibrity.mocapi.server.util;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParametersTest {

    @Test
    void shouldBeRequiredByDefault() throws Exception {
        var method = ParametersTest.class.getMethod("noAnnotation", String.class);
        var parameter = method.getParameters()[0];
        assertThat(Parameters.isRequired(parameter)).isTrue();
    }

    @Test
    void shouldBeOptionalIfNullable() throws Exception {
        var method = ParametersTest.class.getMethod("nullable", String.class);
        var parameter = method.getParameters()[0];
        assertThat(Parameters.isRequired(parameter)).isFalse();
    }

    @Test
    void shouldBeOptionalIfSchemaNotRequired() throws Exception {
        var method = ParametersTest.class.getMethod("schemaNotRequired", String.class);
        var parameter = method.getParameters()[0];
        assertThat(Parameters.isRequired(parameter)).isFalse();
    }

    @Test
    void shouldBeRequiredIfSchemaRequired() throws Exception {
        var method = ParametersTest.class.getMethod("schemaRequired", String.class);
        var parameter = method.getParameters()[0];
        assertThat(Parameters.isRequired(parameter)).isTrue();
    }

    @Test
    void descriptionShouldBeNullIfNotProvidedOnSchemaAnnotation() throws Exception {
        var method = ParametersTest.class.getMethod("schemaRequired", String.class);
        var parameter = method.getParameters()[0];
        var description = Parameters.descriptionOf(parameter);
        assertThat(description).isNull();
    }

    @Test
    void descriptionShouldComeFromSchemaAnnotation() throws Exception {
        var method = ParametersTest.class.getMethod("schemaNotRequired", String.class);
        var parameter = method.getParameters()[0];
        var description = Parameters.descriptionOf(parameter);
        assertThat(description).isEqualTo("A foo parameter");
    }

    @Test
    void titleShouldBeCapitalizedWordsIfNotProvidedOnSchemaAnnotation() throws Exception {
        var method = ParametersTest.class.getMethod("noAnnotation", String.class);
        var parameter = method.getParameters()[0];
        var title = Parameters.titleOf(parameter);
        assertThat(title).isEqualTo("Foo");
    }

    @Test
    void titleShouldComeFromSchemaAnnotation() throws Exception {
        var method = ParametersTest.class.getMethod("schemaNotRequired", String.class);
        var parameter = method.getParameters()[0];
        var title = Parameters.titleOf(parameter);
        assertThat(title).isEqualTo("The Foo");
    }

    public static void noAnnotation(String foo) {
        // Do nothing!
    }

    public static void nullable(@Nullable String foo) {
        // Do nothing!
    }

    public static void schemaNotRequired(@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "A foo parameter", title = "The Foo") String foo){
        // Do nothing!
    }

    public static void schemaRequired(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String foo){
        // Do nothing!
    }
}