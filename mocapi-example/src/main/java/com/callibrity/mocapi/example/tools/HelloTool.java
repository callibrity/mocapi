package com.callibrity.mocapi.example.tools;

import com.callibrity.mocapi.tools.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class HelloTool {

    @Tool
    public HelloResponse sayHello(String name) {
        return new HelloResponse(String.format("Hello, %s!", name));
    }

    public record HelloResponse(String message) { }

}
