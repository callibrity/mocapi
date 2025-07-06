package com.callibrity.mocapi.jsonrpc.method.annotation;

import com.callibrity.mocapi.jsonrpc.method.JsonRpcMethod;
import com.callibrity.mocapi.jsonrpc.method.invoke.JsonMethodInvoker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;

import static java.util.Optional.ofNullable;

public class AnnotationJsonRpcMethod implements JsonRpcMethod {

// ------------------------------ FIELDS ------------------------------

    private final String name;
    private final JsonMethodInvoker invoker;

// --------------------------- CONSTRUCTORS ---------------------------

    AnnotationJsonRpcMethod(ObjectMapper mapper, Object targetObject, Method method) {
        var annotation = ofNullable(method.getAnnotation(JsonRpc.class))
                .orElseThrow(() -> new IllegalArgumentException(String.format("JsonRpc annotation not found on method %s.", method)));
        this.invoker = new JsonMethodInvoker(mapper, targetObject, method);
        this.name = ofNullable(annotation.value())
                .map(StringUtils::stripToNull)
                .orElse(method.getName());
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface JsonRpcMethod ---------------------

    @Override
    public String name() {
        return name;
    }

    @Override
    public JsonNode call(JsonNode params) {
        return invoker.invoke(params);
    }

}
