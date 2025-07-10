# Mocapi

Mocapi is a modular framework for building [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-06-18) tools and prompts using Spring Boot. It simplifies secure, structured interactions between LLMs and services via annotated Java components.

![Maven Central Version](https://img.shields.io/maven-central/v/com.callibrity.mocapi/mocapi-parent)
![GitHub License](https://img.shields.io/github/license/callibrity/mocapi)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=coverage)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)

## Getting Started

Mocapi includes a Spring Boot starter, making it easy to get started by simply adding a dependency:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-spring-boot-starter</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```
By default, Mocapi will listen for MCP requests on the `/mcp` endpoint. You can change this by setting the `ripcurl.endpoint` property:

```properties
ripcurl.endpoint=/your-custom-endpoint
```

## Creating MCP Tools

To create MCP tools using Mocapi, you first need to import the `mocapi-tools` dependency into your project:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-tools</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

This will automatically activate the `MocapiToolsAutoConfiguration` which will enable MCP tools support. To register a
tool, you need to create a bean annotated with `@ToolService` having methods annotated with `@Tool`:

```java
import com.callibrity.mocapi.tools.annotation.Tool;
import com.callibrity.mocapi.tools.annotation.ToolService;
import org.springframework.stereotype.Component;

@Component
@ToolService
public class HelloTool {

    @Tool
    public HelloResponse sayHello(String name) {
        return new HelloResponse(String.format("Hello, %s!", name));
    }

    public record HelloResponse(String message) { }
}
```

## Creating MCP Prompts

To create MCP prompts using Mocapi, you first need to import the `mocapi-prompts` dependency into your project:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-prompts</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

This will automatically activate the `MocapiPromptsAutoConfiguration` which will enable MCP prompts support. To register a
prompt, you need to create a bean annotated with `@PromptService` having methods annotated with `@Prompt`:

```java
import com.callibrity.mocapi.prompts.GetPromptResult;
import com.callibrity.mocapi.prompts.PromptMessage;
import com.callibrity.mocapi.prompts.Role;
import com.callibrity.mocapi.prompts.annotation.Prompt;
import com.callibrity.mocapi.prompts.annotation.PromptService;
import com.callibrity.mocapi.prompts.content.TextContent;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@PromptService
public class CodeReviewPrompts {

    @Prompt(name = "review-code", description = "Provide a short review of the given code snippet")
    public GetPromptResult reviewCode(String language, String code) {
        var prompt = String.format("""
                Please review the following %s code and suggest improvements:
                
                ```%s
                %s
                ```
                """, language, language, code);

        return new GetPromptResult("Provide a short review of the given code snippet", List.of(
                new PromptMessage(Role.USER, new TextContent(prompt))
        ));
    }
}
```

## Building from Source

To build the project locally:

```bash
./mvnw clean install
```

## License

This project is licensed under the Apache License 2.0—see the [LICENSE](LICENSE) file for details.
