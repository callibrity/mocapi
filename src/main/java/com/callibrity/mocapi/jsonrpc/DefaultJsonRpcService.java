package com.callibrity.mocapi.jsonrpc;

import com.callibrity.mocapi.jsonrpc.method.JsonRpcMethod;
import com.callibrity.mocapi.jsonrpc.method.JsonRpcMethodProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Service
public class DefaultJsonRpcService implements JsonRpcService {

// ------------------------------ FIELDS ------------------------------

    private final Map<String, JsonRpcMethod> methods;

// --------------------------- CONSTRUCTORS ---------------------------

    public DefaultJsonRpcService(List<JsonRpcMethodProvider> providers) {
        this.methods = providers.stream()
                .flatMap(provider -> provider.getJsonRpcMethods().stream())
                .collect(Collectors.toMap(JsonRpcMethod::name, m -> m));
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface JsonRpcService ---------------------

    @Override
    public JsonNode call(String method, JsonNode params) {
        return ofNullable(methods.get(method))
                .map(m -> m.call(params))
                .orElseThrow(() -> new IllegalArgumentException(String.format("JSON-RPC method \"%s\" not found.", method)));
    }

}
