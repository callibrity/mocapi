# Writing Prompts

Prompts are reusable message templates that clients can invoke by name, optionally supplying arguments. Define a prompt as a Java method annotated with `@PromptMethod` inside a class annotated with `@PromptService`.

## Defining a Prompt Service

Mark a class with `@PromptService` and register it as a Spring bean:

```java
import com.callibrity.mocapi.api.prompts.PromptMethod;
import com.callibrity.mocapi.api.prompts.PromptService;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@PromptService
public class SummarizationPrompts {

    @PromptMethod(name = "summarize", description = "Summarize the provided text")
    public GetPromptResult summarize(String text) {
        return new GetPromptResult(
            "Text summarization prompt",
            List.of(new PromptMessage(
                Role.USER,
                new TextContent("Summarize the following:\n\n" + text, null))));
    }
}
```

`@PromptService` is a marker -- it does not imply `@Component`. You must also annotate with `@Component`, `@Service`, or register via a `@Bean` method.

## Prompt Method Basics

A `@PromptMethod` method always returns `GetPromptResult`. Method parameters bind to the incoming prompt arguments -- each parameter name matches an argument key.

```java
@PromptMethod(name = "translate", description = "Translate text to a target language")
public GetPromptResult translate(String text, String targetLanguage) {
    return new GetPromptResult(
        "Translation prompt",
        List.of(new PromptMessage(
            Role.USER,
            new TextContent(
                "Translate the following into " + targetLanguage + ":\n\n" + text, null))));
}
```

### Naming

If you omit `name`, the framework generates one from the class and method names. For a class `SummarizationPrompts` with method `summarize`, the generated name is `summarization-prompts.summarize`.

You can also set a `title` and `description`:

```java
@PromptMethod(
    name = "code-review",
    title = "Code Review",
    description = "Review a code snippet for bugs and style issues")
public GetPromptResult codeReview(String code) { ... }
```

### Argument Descriptions

Use Swagger's `@Schema` annotation to document arguments (surfaced in the prompt's descriptor):

```java
import io.swagger.v3.oas.annotations.media.Schema;

@PromptMethod(name = "summarize", description = "Summarize text at a specified detail level")
public GetPromptResult summarize(
    @Schema(description = "The text to summarize") String text,
    @Schema(description = "brief, standard, or detailed") @jakarta.annotation.Nullable Detail detail) {
    ...
}

public enum Detail { BRIEF, STANDARD, DETAILED }
```

### Optional Arguments

By default every parameter is required. Mark a parameter optional with either `@Nullable` or `@Schema(requiredMode = NOT_REQUIRED)`:

```java
@PromptMethod(name = "summarize", description = "Summarize text")
public GetPromptResult summarize(
    String text,
    @Nullable Detail detail) {
    var level = detail == null ? Detail.STANDARD : detail;
    ...
}
```

If the client omits an optional argument, the parameter receives `null`.

## Argument Type Conversion

Prompt arguments arrive on the wire as strings. Mocapi converts each argument to the parameter's declared type via Spring's `ConversionService`, so method parameters can be any type the `ConversionService` knows how to produce from a `String`:

- Strings (no conversion)
- Primitives and boxed primitives (`int`, `long`, `boolean`, `double`, ...)
- Enums (case-insensitive by default)
- `java.time` types (`LocalDate`, `Instant`, ...)
- Anything you register a custom `Converter<String, T>` for

```java
@PromptMethod(name = "schedule", description = "Generate a scheduling prompt")
public GetPromptResult schedule(
    String event,
    LocalDate date,
    @Nullable Duration duration) {
    ...
}
```

If a conversion fails, the client receives a JSON-RPC error describing which argument couldn't be converted.

### Receiving the Whole Arguments Map

If your method declares a single `Map<String, String>` parameter, it receives the entire untyped argument map:

```java
@PromptMethod(name = "dynamic", description = "Pass all arguments through")
public GetPromptResult dynamic(Map<String, String> args) {
    return buildPrompt(args);
}
```

This is useful when argument names are determined dynamically or when you want to sidestep type conversion entirely.

## Return Values

Prompts must return `GetPromptResult`:

```java
public record GetPromptResult(String description, List<PromptMessage> messages) { }
```

Each `PromptMessage` has a `Role` (`USER` or `ASSISTANT`) and `Content`. Content can be `TextContent`, `ImageContent`, `AudioContent`, `EmbeddedResource`, or `ResourceLink`.

### Multi-Message Prompts

A prompt can emit a conversation, not just a single message:

```java
@PromptMethod(name = "few-shot", description = "Few-shot classification prompt")
public GetPromptResult fewShot(String input) {
    return new GetPromptResult(
        "Few-shot classification",
        List.of(
            new PromptMessage(Role.USER, new TextContent("Classify: 'I love this!'", null)),
            new PromptMessage(Role.ASSISTANT, new TextContent("positive", null)),
            new PromptMessage(Role.USER, new TextContent("Classify: 'Terrible.'", null)),
            new PromptMessage(Role.ASSISTANT, new TextContent("negative", null)),
            new PromptMessage(Role.USER, new TextContent("Classify: '" + input + "'", null))));
}
```

### Embedded Resources

Reference a resource inline:

```java
import com.callibrity.mocapi.model.EmbeddedResource;
import com.callibrity.mocapi.model.TextResourceContents;

@PromptMethod(name = "analyze-doc", description = "Analyze an embedded document")
public GetPromptResult analyzeDoc(String uri) {
    return new GetPromptResult(
        "Document analysis",
        List.of(
            new PromptMessage(
                Role.USER,
                new EmbeddedResource(
                    new TextResourceContents(uri, "text/plain", loadDocument(uri)),
                    null)),
            new PromptMessage(
                Role.USER,
                new TextContent("Analyze the document above.", null))));
}
```
