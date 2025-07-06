package com.callibrity.mocapi.jsonrpc.method;

import java.util.List;

public interface JsonRpcMethodProvider {
    List<JsonRpcMethod> getJsonRpcMethods();
}
