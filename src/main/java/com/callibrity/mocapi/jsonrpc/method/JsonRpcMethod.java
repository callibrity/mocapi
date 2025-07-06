package com.callibrity.mocapi.jsonrpc.method;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonRpcMethod {
    String name();
    JsonNode call(JsonNode params);
}
