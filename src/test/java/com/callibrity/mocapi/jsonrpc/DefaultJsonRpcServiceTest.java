package com.callibrity.mocapi.jsonrpc;

import com.callibrity.mocapi.jsonrpc.method.annotation.AnnotationJsonRpcMethodProvider;
import com.callibrity.mocapi.jsonrpc.method.annotation.JsonRpc;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultJsonRpcServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void callingMissingMethod() {
        var service = new DefaultJsonRpcService(List.of());
        var params = NullNode.getInstance();
        assertThatThrownBy(() -> service.call("foo", params))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void callAnnotatedNamedMethod() {
        var dummy = new DummyService();
        var service = new DefaultJsonRpcService(List.of(new AnnotationJsonRpcMethodProvider(mapper, dummy)));

        var params = JsonNodeFactory.instance.objectNode();
        params.put("input", "Hello");

        var answer = service.call("foo", params);

        assertThat(answer).isEqualTo(TextNode.valueOf("Hello"));

    }

    @Test
    void callAnnotatedUnnamedMethod() {
        var dummy = new DummyService();
        var service = new DefaultJsonRpcService(List.of(new AnnotationJsonRpcMethodProvider(mapper, dummy)));

        var params = JsonNodeFactory.instance.objectNode();
        params.put("input", "Hello");

        var answer = service.call("unnamed", params);

        assertThat(answer).isEqualTo(TextNode.valueOf("Hello"));

    }

    @Test
    void schemaGenerator() throws Exception {

        var config = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON)
                .withObjectMapper(mapper)
                .with(new JacksonModule())
                .with(new JakartaValidationModule())
                .with(new Swagger2Module())
                .build();

        var generator = new SchemaGenerator(config);
        var echo = new StringEcho();
        var method = Arrays.stream(StringEcho.class.getMethods()).filter(m -> m.getName().equals("echo")).findFirst().orElseThrow(() -> new IllegalArgumentException("Unable to find method."));

        var schema = generateParameterSchema(generator, mapper, echo, method);
        assertThat(schema).isNotNull();
        System.out.println(schema.toPrettyString());
    }

    public static Class<?>[] resolveMethodParameterTypes(Object targetObject, Method method) {
        var assigningType = targetObject.getClass();
        return Arrays.stream(method.getGenericParameterTypes()).map(t -> TypeUtils.getRawType(t, assigningType)).toArray(Class[]::new);
    }

    public static JsonNode generateParameterSchema(SchemaGenerator generator, ObjectMapper mapper, Object targetObject, Method method) {
        var parameters = method.getParameters();

        var parameterTypes = resolveMethodParameterTypes(targetObject, method);

        var schemaNode = mapper.createObjectNode();
        var propsNode = mapper.createObjectNode();
        var requiredNode = mapper.createArrayNode();


        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];

            var paramSchema = generator.generateSchema(parameterTypes[i]);

            propsNode.set(param.getName(), paramSchema);
            if (param.isAnnotationPresent(NotNull.class) || (param.isAnnotationPresent(Schema.class) && param.getAnnotation(Schema.class).requiredMode() == Schema.RequiredMode.REQUIRED)) {
                requiredNode.add(param.getName());
            }
        }
        schemaNode.put("type", "object");
        schemaNode.set("properties", propsNode);
        if (!requiredNode.isEmpty()) {
            schemaNode.set("required", requiredNode);
        }

        return schemaNode;
    }

    public static abstract class BaseEcho<T> {
        public T echo(T original, Integer count) {
            return original;
        }
    }

    public static class StringEcho extends BaseEcho<String> {

    }

    public static class DummyService {
        @JsonRpc
        public String unnamed(@NotNull String input) {
            return input;
        }

        public String multi(String input, LocalDate date) {
            return input;
        }

        @JsonRpc("foo")
        public String named(String input) {
            return input;
        }

    }

}