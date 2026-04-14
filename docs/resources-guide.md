# Writing Resources

Resources let MCP clients fetch content by URI. Mocapi supports two flavors:

- **Fixed resources** -- a specific URI maps to a specific method (`@ResourceMethod`).
- **Templated resources** -- a URI template captures variables from the request and passes them to the method (`@ResourceTemplateMethod`).

Both live in a class annotated with `@ResourceService`.

## Defining a Resource Service

Mark a class with `@ResourceService` and register it as a Spring bean. The same class can mix fixed and templated resource methods:

```java
import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ResourceService
public class DocumentResources {

    @ResourceMethod(
        uri = "docs://readme",
        name = "README",
        description = "Project README",
        mimeType = "text/markdown")
    public ReadResourceResult readme() {
        return new ReadResourceResult(
            List.of(new TextResourceContents(
                "docs://readme", "text/markdown", loadReadme())));
    }

    @ResourceTemplateMethod(
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

`@ResourceService` is a marker -- it does not imply `@Component`. You must also annotate with `@Component`, `@Service`, or register via a `@Bean` method.

## Fixed Resources (`@ResourceMethod`)

A fixed resource has a concrete URI and no arguments. The method takes no parameters and returns a `ReadResourceResult`:

```java
@ResourceMethod(
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

## Templated Resources (`@ResourceTemplateMethod`)

A templated resource declares an RFC 6570 URI template with `{placeholders}`. Method parameters named to match the placeholders receive the extracted values:

```java
@ResourceTemplateMethod(
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
@ResourceTemplateMethod(uriTemplate = "users://{userId}/posts/{postId}")
public ReadResourceResult userPost(long userId, UUID postId) {
    ...
}
```

If a conversion fails (for example, the client reads `users://abc/posts/xyz` but `userId` is declared `long`), Mocapi raises a resolution error describing which variable couldn't be converted.

### Receiving the Whole Path-Variable Map

If the method declares a single `Map<String, String>` parameter, it receives all extracted path variables untyped:

```java
@ResourceTemplateMethod(uriTemplate = "files://{bucket}/{+path}")
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

## Return Values

Both kinds of resource methods must return `ReadResourceResult`:

```java
public record ReadResourceResult(List<ResourceContents> contents) { }
```

`ResourceContents` is a sealed type with two variants:

- `TextResourceContents(String uri, String mimeType, String text)` -- for text content
- `BlobResourceContents(String uri, String mimeType, String blob)` -- for binary content (base64-encoded)

A single resource can return multiple `ResourceContents` entries (for example, a markdown page plus its embedded images).

```java
@ResourceMethod(uri = "report://latest", mimeType = "application/pdf")
public ReadResourceResult latestReport() {
    byte[] pdf = reportService.generate();
    var encoded = Base64.getEncoder().encodeToString(pdf);
    return new ReadResourceResult(
        List.of(new BlobResourceContents("report://latest", "application/pdf", encoded)));
}
```

## URI Template Matching

Mocapi uses Spring's `UriTemplate` for matching. Fixed resources are matched first by exact URI; if no fixed resource matches, each registered template is tried in registration order until one matches. The first match wins.

If no resource or template matches the requested URI, the client receives a JSON-RPC `Invalid params` error.
