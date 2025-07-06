package com.callibrity.mocapi.jsonrpc.method.invoke;

public class JsonInvocationException extends RuntimeException {

// --------------------------- CONSTRUCTORS ---------------------------

    public JsonInvocationException(Exception cause, String message, Object... params) {
        super(String.format(message, params), cause);
    }

    public JsonInvocationException(String message, Object... params) {
        super(String.format(message, params));
    }
}
