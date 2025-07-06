package com.callibrity.mocapi.jsonrpc.method.invoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import lombok.Getter;

import java.io.IOException;
import java.lang.reflect.Parameter;

public class JsonParameterMapper {
    @Getter
    private final String parameterName;
    private final ObjectMapper mapper;
    private final ObjectReader reader;

    public JsonParameterMapper(ObjectMapper mapper, Parameter parameter) {
        this.parameterName = parameter.getName();
        this.mapper = mapper;
        this.reader = mapper.readerFor(parameter.getType());
    }

    public Object mapParameter(JsonNode node) {
        if(node == null || node.isNull()) {
            return null;
        }

        try {
            return reader.readValue(mapper.treeAsTokens(node));
        } catch (IOException e) {
            throw new JsonInvocationException(e, "Unable to map parameter %s.", parameterName);
        }
    }
}
