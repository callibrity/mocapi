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

## Externalizing Metadata

Every string attribute on `@PromptMethod` (`name`, `title`, `description`) supports Spring's `${...}` property placeholder syntax, so long descriptions don't have to live inline on the annotation. See [Externalizing Annotation Metadata](externalizing-metadata.md).

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

## Prompt Templates

For anything beyond trivial string concatenation, use a `PromptTemplate`. Mocapi ships two engine implementations; each provides a `PromptTemplateFactory` Spring bean that you inject into your `@PromptService`.

### The core interfaces

Both live in `com.callibrity.mocapi.api.prompts.template`:

```java
public interface PromptTemplate {
    GetPromptResult render(Map<String, String> args);
}

public interface PromptTemplateFactory {
    PromptTemplate create(Role role, String description, String template);
    default PromptTemplate create(Role role, String template) { ... }
}
```

The factory takes raw template source as a `String` — you load it from wherever you like (classpath, filesystem, database, inline literal). Compiled templates are reusable: `create(...)` once at construction, `render(...)` many times.

### Available engines

| Module | Engine | Syntax | Features |
|--------|--------|--------|----------|
| `mocapi-prompts-spring` | Spring's `PropertyPlaceholderHelper` | `${name}`, `${name:default}`, `\${name}` escape | Zero extra dependencies; pure substitution |
| `mocapi-prompts-mustache` | [JMustache](https://github.com/samskivert/jmustache) | `{{name}}`, `{{#section}}...{{/section}}`, partials | Conditionals and iteration via sections |

Add one to your build:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-prompts-spring</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

Its auto-configuration registers a default `PromptTemplateFactory` bean. If both modules are on the classpath, only the first one seen wins — most apps should pick one. Users with their own bean override both by declaring `@Bean PromptTemplateFactory` (our auto-configs use `@ConditionalOnMissingBean`).

### Using a template from a `@PromptMethod`

Compile templates once at construction. Render them inside the method:

```java
import com.callibrity.mocapi.api.prompts.template.PromptTemplate;
import com.callibrity.mocapi.api.prompts.template.PromptTemplateFactory;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Role;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@PromptService
public class SummarizationPrompts {

    private final PromptTemplate summarize;

    public SummarizationPrompts(PromptTemplateFactory factory) throws IOException {
        var source =
            new ClassPathResource("prompts/summarize.mustache")
                .getContentAsString(StandardCharsets.UTF_8);
        this.summarize = factory.create(Role.USER, "Summarize the provided text", source);
    }

    @PromptMethod(name = "summarize", description = "Summarize text")
    public GetPromptResult summarize(String text, @Nullable Detail detail) {
        return summarize.render(Map.of(
            "text", text,
            "detail", detail == null ? "standard" : detail.name().toLowerCase()));
    }
}
```

The template source in `src/main/resources/prompts/summarize.mustache`:

```
Summarize the following text at {{detail}} detail:

{{text}}
```

### Multi-message templates

A single template renders into exactly one `PromptMessage` with the role supplied at `create(...)` time. For multi-message prompts, compile several templates and compose them:

```java
public FewShotPrompts(PromptTemplateFactory factory) {
    this.intro = factory.create(Role.USER, load("intro.mustache"));
    this.userTurn = factory.create(Role.USER, load("user-turn.mustache"));
    this.assistantTurn = factory.create(Role.ASSISTANT, load("assistant-turn.mustache"));
}

@PromptMethod(name = "few-shot")
public GetPromptResult fewShot(String input) {
    var messages = new ArrayList<PromptMessage>();
    messages.addAll(intro.render(Map.of()).messages());
    messages.addAll(userTurn.render(Map.of("text", "I love this!")).messages());
    messages.addAll(assistantTurn.render(Map.of("label", "positive")).messages());
    messages.addAll(userTurn.render(Map.of("text", input)).messages());
    return new GetPromptResult("Few-shot classification", messages);
}
```

### Customizing a factory

Both factories expose a constructor that accepts a pre-configured engine object, so you can register a custom `PromptTemplateFactory` bean when the defaults aren't right. For example, to use `{{name}}` delimiters with the Spring-based engine:

```java
@Bean
PromptTemplateFactory promptTemplateFactory() {
    return new SpringPromptTemplateFactory(
        new PropertyPlaceholderHelper("{{", "}}", ":", '\\', true));
}
```

Your bean wins thanks to `@ConditionalOnMissingBean(PromptTemplateFactory.class)`.
