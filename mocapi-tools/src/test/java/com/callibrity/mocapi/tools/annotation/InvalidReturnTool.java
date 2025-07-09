package com.callibrity.mocapi.tools.annotation;

public class InvalidReturnTool {

    @Tool
    public String badReturnType(String name) {
        return String.format("Hello, %s!", name);
    }
}
