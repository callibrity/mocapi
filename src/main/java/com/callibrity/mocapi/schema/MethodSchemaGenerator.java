package com.callibrity.mocapi.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;

public interface MethodSchemaGenerator {

    ObjectNode generateInputSchema(Object targetObject, Method method);
    ObjectNode generateOutputSchema(Object targetObject, Method method);
}
