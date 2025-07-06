package com.callibrity.mocapi.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

public interface JsonRpcService {
   JsonNode call(String method, JsonNode params);
}
