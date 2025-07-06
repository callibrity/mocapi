package com.callibrity.mocapi.jsonrpc.method.annotation;

import com.callibrity.mocapi.jsonrpc.method.JsonRpcMethod;
import com.callibrity.mocapi.jsonrpc.method.JsonRpcMethodProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class AnnotationJsonRpcMethodProvider implements JsonRpcMethodProvider {

// ------------------------------ FIELDS ------------------------------

    private final List<AnnotationJsonRpcMethod> methods;

// --------------------------- CONSTRUCTORS ---------------------------

    public AnnotationJsonRpcMethodProvider(ObjectMapper mapper, Object targetObject) {
        this.methods = Arrays.stream(targetObject.getClass().getMethods())
                .filter(m -> m.isAnnotationPresent(JsonRpc.class))
                .map(m -> new AnnotationJsonRpcMethod(mapper, targetObject, m))
                .toList();
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface JsonRpcMethodProvider ---------------------

    @Override
    public List<JsonRpcMethod> getJsonRpcMethods() {
        return List.copyOf(methods);
    }

}
