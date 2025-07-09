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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;


public class DefaultMethodSchemaGenerator implements MethodSchemaGenerator {

// ------------------------------ FIELDS ------------------------------

    public static final String SCHEMA_PROPERTY_NAME = "$schema";
    private final SchemaGenerator generator;

// --------------------------- CONSTRUCTORS ---------------------------

    public DefaultMethodSchemaGenerator(ObjectMapper mapper, SchemaVersion schemaVersion) {
        this.generator = new SchemaGenerator(new SchemaGeneratorConfigBuilder(schemaVersion, OptionPreset.PLAIN_JSON)
                .withObjectMapper(mapper)
                .with(new JacksonModule())
                .with(new JakartaValidationModule())
                .with(new Swagger2Module())
                .build());
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface MethodSchemaGenerator ---------------------

    @Override
    public ObjectNode generateInputSchema(Object targetObject, Method method) {
        var mapper = generator.getConfig().getObjectMapper();

        var schemaNode = mapper.createObjectNode();
        schemaNode.set(SCHEMA_PROPERTY_NAME, TextNode.valueOf(generator.getConfig().getSchemaVersion().getIdentifier()));

        var propsNode = mapper.createObjectNode();
        var requiredNode = mapper.createArrayNode();

        var parameters = method.getParameters();
        var assigningType = targetObject.getClass();
        var parameterTypes = Arrays.stream(method.getGenericParameterTypes()).map(t -> getRawType(t, assigningType)).toArray(Class[]::new);

        for (int i = 0; i < parameters.length; i++) {
            var param = parameters[i];
            var paramSchemaNode =  generator.generateSchema(parameterTypes[i]);
            paramSchemaNode.remove(SCHEMA_PROPERTY_NAME);
            propsNode.set(param.getName(), paramSchemaNode);
            if(param.isAnnotationPresent(Schema.class)) {
                var annotation =  param.getAnnotation(Schema.class);
                if(StringUtils.isNotBlank(annotation.title())) {
                    paramSchemaNode.put("title", annotation.title());
                }
                if(StringUtils.isNotBlank(annotation.description())) {
                    paramSchemaNode.put("description", annotation.description());
                }
            }
            requiredNode.add(param.getName());
        }
        schemaNode.put("type", "object");
        schemaNode.set("properties", propsNode);
        if (!requiredNode.isEmpty()) {
            schemaNode.set("required", requiredNode);
        }
        return schemaNode;
    }

    @Override
    public ObjectNode generateOutputSchema(Object targetObject, Method method) {
        return generator.generateSchema(getRawType(method.getGenericReturnType(), targetObject.getClass()));
    }

// -------------------------- OTHER METHODS --------------------------

    private Class<?> getRawType(Type genericType, Class<?> assigningType) {
        return TypeUtils.getRawType(genericType, assigningType);
    }

}
