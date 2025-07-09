package com.callibrity.mocapi.tools.util;

import com.callibrity.mocapi.tools.annotation.Tool;
import io.swagger.v3.oas.annotations.media.Schema;

public class HelloTool {
    @Tool
    public HelloResponse sayHello(@Schema(name = "Name", description = "The person's name") String name) {
        return new HelloResponse(String.format("Hello, %s!", name));
    }

}
