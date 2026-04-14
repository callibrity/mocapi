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
package com.callibrity.mocapi.server.tools.schema;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import org.apache.commons.lang3.reflect.TypeUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

public class DefaultMethodSchemaGenerator implements MethodSchemaGenerator {

  public static final String SCHEMA_PROPERTY_NAME = "$schema";
  public static final String REQUIRED_PROPERTY = "required";
  public static final String TYPE_PROPERTY = "type";
  public static final String OBJECT_TYPE = "object";
  public static final String PROPERTIES_PROPERTY = "properties";
  private final SchemaGenerator generator;

  public DefaultMethodSchemaGenerator(ObjectMapper mapper, SchemaVersion schemaVersion) {
    this.generator =
        new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(mapper, schemaVersion, OptionPreset.PLAIN_JSON)
                .with(new JacksonSchemaModule())
                .with(new JakartaValidationModule())
                .with(new Swagger2Module())
                .build());
  }

  @Override
  public ObjectNode generateInputSchema(Object targetObject, Method method) {
    Parameter mcpToolParamsParam = findMcpToolParamsParameter(method);
    if (mcpToolParamsParam != null) {
      return generateInputSchemaFromRecord(targetObject, mcpToolParamsParam);
    }
    return generateInputSchemaFromParameters(targetObject, method);
  }

  private Parameter findMcpToolParamsParameter(Method method) {
    Parameter found = null;
    for (Parameter param : method.getParameters()) {
      if (param.isAnnotationPresent(McpToolParams.class)) {
        found = param;
        break;
      }
    }
    return found;
  }

  private ObjectNode generateInputSchemaFromRecord(Object targetObject, Parameter recordParam) {
    Class<?> recordType = getRawType(recordParam.getParameterizedType(), targetObject.getClass());
    var schema = generator.generateSchema(recordType);
    schema.remove(SCHEMA_PROPERTY_NAME);
    var mapper = generator.getConfig().getObjectMapper();
    var schemaNode = mapper.createObjectNode();
    schemaNode.set(
        SCHEMA_PROPERTY_NAME,
        StringNode.valueOf(generator.getConfig().getSchemaVersion().getIdentifier()));
    schemaNode.put(TYPE_PROPERTY, OBJECT_TYPE);
    schemaNode.set(PROPERTIES_PROPERTY, schema.get(PROPERTIES_PROPERTY));
    if (schema.has(REQUIRED_PROPERTY)) {
      schemaNode.set(REQUIRED_PROPERTY, schema.get(REQUIRED_PROPERTY));
    }
    return schemaNode;
  }

  private ObjectNode generateInputSchemaFromParameters(Object targetObject, Method method) {
    var mapper = generator.getConfig().getObjectMapper();

    var schemaNode = mapper.createObjectNode();
    schemaNode.set(
        SCHEMA_PROPERTY_NAME,
        StringNode.valueOf(generator.getConfig().getSchemaVersion().getIdentifier()));

    var propsNode = mapper.createObjectNode();
    var requiredNode = mapper.createArrayNode();

    var parameters = method.getParameters();
    var assigningType = targetObject.getClass();
    var parameterTypes =
        Arrays.stream(method.getGenericParameterTypes())
            .map(t -> getRawType(t, assigningType))
            .toArray(Class[]::new);

    for (int i = 0; i < parameters.length; i++) {
      var param = parameters[i];
      if (McpToolContext.class.isAssignableFrom(parameterTypes[i])) {
        continue;
      }
      var paramSchemaNode = generator.generateSchema(parameterTypes[i]);
      paramSchemaNode.remove(SCHEMA_PROPERTY_NAME);
      putIfNotNull(paramSchemaNode, "title", Parameters.titleOf(param));
      putIfNotNull(paramSchemaNode, "description", Parameters.descriptionOf(param));
      propsNode.set(param.getName(), paramSchemaNode);
      if (Parameters.isRequired(param)) {
        requiredNode.add(param.getName());
      }
    }
    schemaNode.put(TYPE_PROPERTY, OBJECT_TYPE);
    schemaNode.set(PROPERTIES_PROPERTY, propsNode);
    if (!requiredNode.isEmpty()) {
      schemaNode.set(REQUIRED_PROPERTY, requiredNode);
    }
    return schemaNode;
  }

  private void putIfNotNull(ObjectNode node, String key, String value) {
    if (value != null) {
      node.put(key, value);
    }
  }

  @Override
  public ObjectNode generateOutputSchema(Object targetObject, Method method) {
    return generator.generateSchema(
        getRawType(method.getGenericReturnType(), targetObject.getClass()));
  }

  @Override
  public ObjectNode generateSchema(Class<?> type) {
    return generator.generateSchema(type);
  }

  private Class<?> getRawType(Type genericType, Class<?> assigningType) {
    return TypeUtils.getRawType(genericType, assigningType);
  }
}
