# Writing Resources

Resources let MCP clients fetch content by URI. Mocapi supports two flavors:

- **Fixed resources** -- a specific URI maps to a specific method (`@McpResource`).
- **Templated resources** -- a URI template captures variables from the request and passes them to the method (`@McpResourceTemplate`).

Both live on a Spring bean — no class-level marker annotation needed.

## Defining Resources

Annotate methods with `@McpResource` and/or `@McpResourceTemplate` and register the enclosing class as a Spring bean. The same class can mix fixed and templated resource methods:

```java
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentResources {

    @McpResource(
        uri = "docs://readme",
        name = "README",
        description = "Project README",
        mimeType = "text/markdown")
    public ReadResourceResult readme() {
        return new ReadResourceResult(
            List.of(new TextResourceContents(
                "docs://readme", "text/markdown", loadReadme())));
    }

    @McpResourceTemplate(
        uriTemplate = "docs://pages/{slug}",
        name = "Documentation Page",
        description = "Individual documentation page",
        mimeType = "text/markdown")
    public ReadResourceResult page(String slug) {
        var content = loadPage(slug);
        var uri = "docs://pages/" + slug;
        return new ReadResourceResult(
            List.of(new TextResourceContents(uri, "text/markdown", content)));
    }
}
```

Any bean-hood mechanism works — `@Component`, `@Service`, or a `@Bean` factory method. The framework scans every bean for `@McpResource` and `@McpResourceTemplate` methods and registers one handler per annotated method.

Each registered resource, resource template, and enum-typed URI-variable's completion candidates is logged at `INFO` level during startup. See [Startup Logging](architecture.md#startup-logging) for the full catalog.

## Fixed Resources (`@McpResource`)

A fixed resource has a concrete URI and no arguments. The method takes no parameters and returns a `ReadResourceResult`:

```java
@McpResource(
    uri = "config://app/version",
    name = "App Version",
    description = "Current application version",
    mimeType = "text/plain")
public ReadResourceResult version() {
    return new ReadResourceResult(
        List.of(new TextResourceContents(
            "config://app/version", "text/plain", buildInfo.getVersion())));
}
```

Fixed resources appear in the client's `resources/list` response and can be read directly by URI.

### Attributes

| Attribute | Required | Description |
|-----------|----------|-------------|
| `uri` | yes | The fully-qualified resource URI |
| `name` | no | Human-readable name. Defaults to a generated name from the class and method |
| `description` | no | Description shown in the resource list. Defaults to `name` |
| `mimeType` | no | The content MIME type |

## Templated Resources (`@McpResourceTemplate`)

A templated resource declares an RFC 6570 URI template with `{placeholders}`. Method parameters named to match the placeholders receive the extracted values:

```java
@McpResourceTemplate(
    uriTemplate = "users://{userId}/profile",
    name = "User Profile",
    mimeType = "application/json")
public ReadResourceResult userProfile(String userId) {
    var profile = userService.getProfile(userId);
    return new ReadResourceResult(
        List.of(new TextResourceContents(
            "users://" + userId + "/profile",
            "application/json",
            toJson(profile))));
}
```

When a client reads `users://42/profile`, Mocapi matches the template, extracts `{userId: "42"}`, and invokes the method with `userId = "42"`.

### Path Variable Type Conversion

Path variables arrive as strings. Mocapi converts each variable to the parameter's declared type via Spring's `ConversionService`, so method parameters can be any type the `ConversionService` knows how to produce from a `String`:

- Strings (no conversion)
- Primitives and boxed primitives (`int`, `long`, `boolean`, ...)
- Enums
- `java.time` types
- Anything you register a custom `Converter<String, T>` for

```java
@McpResourceTemplate(uriTemplate = "users://{userId}/posts/{postId}")
public ReadResourceResult userPost(long userId, UUID postId) {
    ...
}
```

If a conversion fails (for example, the client reads `users://abc/posts/xyz` but `userId` is declared `long`), Mocapi raises a resolution error describing which variable couldn't be converted.

### Receiving the Whole Path-Variable Map

If the method declares a single `Map<String, String>` parameter, it receives all extracted path variables untyped:

```java
@McpResourceTemplate(uriTemplate = "files://{bucket}/{+path}")
public ReadResourceResult file(Map<String, String> vars) {
    return readFile(vars.get("bucket"), vars.get("path"));
}
```

### Attributes

| Attribute | Required | Description |
|-----------|----------|-------------|
| `uriTemplate` | yes | RFC 6570 URI template |
| `name` | no | Human-readable name. Defaults to a generated name |
| `description` | no | Description. Defaults to `name` |
| `mimeType` | no | The content MIME type |

## Externalizing Metadata

Every string attribute on `@McpResource` (`uri`, `name`, `title`, `description`, `mimeType`) and `@McpResourceTemplate` (`uriTemplate`, `name`, `title`, `description`, `mimeType`) supports Spring's `${...}` property placeholder syntax, so URIs, long descriptions, and mime types can live in `application.yml` instead of inline on the annotation. See [Externalizing Annotation Metadata](externalizing-metadata.md).

## Path Variable Completions (autocomplete)

When a `@McpResourceTemplate`'s URI template has a variable typed as a Java `enum` (or marked with `@Schema(allowableValues = {...})` on a `String`), mocapi registers those values as completion candidates for the MCP `completion/complete` RPC:

```java
public enum Environment { DEV, STAGE, PROD }

@McpResourceTemplate(uriTemplate = "env://{stage}/config")
public ReadResourceResult config(Environment stage) { ... }
```

An MCP client asking for completions on the `{stage}` variable gets `["DEV", "STAGE", "PROD"]`, prefix-filtered. At read time the same enum values bind through Spring's `ConversionService`, so the completions and the actual binding can't drift.

## Return Values

Both kinds of resource methods must return `ReadResourceResult`:

```java
public record ReadResourceResult(List<ResourceContents> contents) { }
```

`ResourceContents` is a sealed type with two variants:

- `TextResourceContents(String uri, String mimeType, String text)` -- for text content
- `BlobResourceContents(String uri, String mimeType, String blob)` -- for binary content (base64-encoded)

A single resource can return multiple `ResourceContents` entries (for example, a markdown page plus its embedded images).

### Convenience factories

For the common single-entry case, `ReadResourceResult` provides static factory methods that collapse the wrapping boilerplate:

```java
ReadResourceResult.ofText(uri, mimeType, text);
ReadResourceResult.ofBlob(uri, mimeType, byte[] bytes);   // auto-base64
ReadResourceResult.ofBlob(uri, mimeType, String base64);  // if already encoded
```

So the typical text resource becomes a one-liner:

```java
@McpResource(uri = "docs://readme", mimeType = "text/markdown")
public ReadResourceResult readme() {
    return ReadResourceResult.ofText("docs://readme", "text/markdown", loadReadme());
}
```

And binary resources no longer need manual `Base64` encoding:

```java
@McpResource(uri = "report://latest", mimeType = "application/pdf")
public ReadResourceResult latestReport() {
    return ReadResourceResult.ofBlob(
        "report://latest", "application/pdf", reportService.generate());
}
```

For multi-entry results (e.g., a markdown page plus its embedded images), use the plain record constructor and assemble the list yourself.

## URI Template Matching

Mocapi uses Spring's `UriTemplate` for matching. Fixed resources are matched first by exact URI; if no fixed resource matches, each registered template is tried in registration order until one matches. The first match wins.

If no resource or template matches the requested URI, the client receives a JSON-RPC `Invalid params` error.
