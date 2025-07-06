package com.callibrity.mocapi.jsonrpc.method;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultJsonRpcMethodProvider implements JsonRpcMethodProvider {

// ------------------------------ FIELDS ------------------------------

    private final List<JsonRpcMethod> beans;

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface JsonRpcMethodProvider ---------------------

    @Override
    public List<JsonRpcMethod> getJsonRpcMethods() {
        return List.copyOf(beans);
    }

}
