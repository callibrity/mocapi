package com.callibrity.mocapi.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;

@Service
public class DefaultMethodSchemaGenerator implements MethodSchemaGenerator {

// ------------------------------ FIELDS ------------------------------

    private final SchemaGenerator generator;

// --------------------------- CONSTRUCTORS ---------------------------

    public DefaultMethodSchemaGenerator(ObjectMapper mapper) {
        this.generator = new SchemaGenerator(new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON)
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
        var propsNode = mapper.createObjectNode();
        var requiredNode = mapper.createArrayNode();

        var parameters = method.getParameters();
        var assigningType = targetObject.getClass();
        var parameterTypes = Arrays.stream(method.getGenericParameterTypes()).map(t -> getRawType(t, assigningType)).toArray(Class[]::new);

        for (int i = 0; i < parameters.length; i++) {
            var param = parameters[i];
            var propSchemaNode =  generator.generateSchema(parameterTypes[i]);
            schemaNode.set("$schema", propSchemaNode.get("$schema"));
            propSchemaNode.remove("$schema");
            propsNode.set(param.getName(), propSchemaNode);
            if(param.isAnnotationPresent(Schema.class)) {
                var annotation =  param.getAnnotation(Schema.class);
                if(StringUtils.isNotBlank(annotation.title())) {
                    propSchemaNode.put("title", annotation.title());
                }
                if(StringUtils.isNotBlank(annotation.description())) {
                    propSchemaNode.put("description", annotation.description());
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
