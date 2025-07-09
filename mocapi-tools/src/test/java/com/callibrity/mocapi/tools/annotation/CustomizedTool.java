package com.callibrity.mocapi.tools.annotation;

import com.callibrity.mocapi.tools.util.HelloResponse;

public class CustomizedTool {
    @Tool(name="custom name", title = "Custom Title", description = "Custom description of a tool")
    public HelloResponse sayHello(String name) {
        return new HelloResponse(String.format("Hello, %s!", name));
    }

}
