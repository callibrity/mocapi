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

Each registered resource, resource template, and enum-typed URI-variable's completion candidates is logged at `INFO` level during startup. See [Startup Logging](../design/architecture-overview.md#startup-logging) for the full catalog.

## Fixed Resources (`@McpResource`)

A fixed resource has a concrete URI and no arguments. The method takes no parameters and returns its content — a `ReadResourceResult` or one of the convenience return types (see [Return Values](#return-values)):

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

The full-control return type for either kind of resource is `ReadResourceResult`:

```java
public record ReadResourceResult(List<ResourceContents> contents) { }
```

`ResourceContents` is a sealed type with two variants:

- `TextResourceContents(String uri, String mimeType, String text)` -- for text content
- `BlobResourceContents(String uri, String mimeType, String blob)` -- for binary content (base64-encoded)

A single resource can return multiple `ResourceContents` entries (for example, a markdown page plus its embedded images) — that's the reason to reach for `ReadResourceResult` directly.

### Convenience return types

Both a fixed-URI `@McpResource` and a `@McpResourceTemplate` method can skip the `ReadResourceResult` wrapping entirely and just return the payload — mocapi wraps it against the resource's `mimeType` and its URI (the annotation's `uri` for a fixed resource, or the **matched request URI** for a template):

| Return type | Becomes | Notes |
|-------------|---------|-------|
| `String` / `CharSequence` | text content | |
| `byte[]` / `ByteBuffer` | blob content | auto-base64; `ByteBuffer` is read non-destructively |
| `org.springframework.core.io.Resource` | text or blob | Spring resource (classpath/file/URL); text vs. blob per `content` (below) |
| `ReadResourceResult` | itself | full control — the escape hatch for multi-entry, custom cache directives, etc. |

So the typical text resource is a one-liner with no factory call:

```java
@McpResource(uri = "docs://readme", mimeType = "text/markdown")
public String readme() {
    return loadReadme();
}
```

Binary resources return raw bytes — no manual `Base64`:

```java
@McpResource(uri = "report://latest", mimeType = "application/pdf")
public byte[] latestReport() {
    return reportService.generate();
}
```

And a file on the classpath is just a `Resource`:

```java
@McpResource(uri = "docs://logo", mimeType = "image/png")
public org.springframework.core.io.Resource logo() {
    return new ClassPathResource("static/logo.png");
}
```

Templated resources work the same — the return is wrapped against the **matched
request URI**, so `docs://pages/intro` is stamped into the contents automatically:

```java
@McpResourceTemplate(uriTemplate = "docs://pages/{slug}", mimeType = "text/markdown")
public String page(String slug) {
    return loadPage(slug);
}
```

#### `content` — text vs. blob for a `Resource` return

A returned Spring `Resource` is opaque bytes, so `@McpResource(content = ...)` decides how to represent it:

- `AUTO` (default) — infer from the declared `mimeType`: a `text/*` base type, or a `json` / `xml` / `javascript` / `ecmascript` subtype (including `+json` / `+xml` structured suffixes), is text; anything else — including a blank/unknown/malformed mime — is blob. Text is decoded UTF-8, honoring a `charset` mime parameter if present.
- `TEXT` / `BLOB` — force it, ignoring the mime type.

```java
@McpResource(uri = "ui://widget", mimeType = "text/html", content = ResourceContent.TEXT)
public org.springframework.core.io.Resource widget() {
    return new ClassPathResource("ui/widget.html");
}
```

### `ReadResourceResult` factories

When you do build a `ReadResourceResult` by hand — multi-entry results, or custom cache directives — the static factories collapse the single-entry boilerplate:

```java
ReadResourceResult.ofText(uri, mimeType, text);
ReadResourceResult.ofBlob(uri, mimeType, byte[] bytes);   // auto-base64
ReadResourceResult.ofBlob(uri, mimeType, String base64);  // if already encoded
```

Inside a template that returns `ReadResourceResult` directly, the request URI is available via `McpResourceContext`, so `ofText(ctx.uri(), …)` stays a one-liner. For multi-entry results, use the plain record constructor and assemble the list yourself.

## URI Template Matching

Mocapi uses Spring's `UriTemplate` for matching. Fixed resources are matched first by exact URI; if no fixed resource matches, each registered template is tried in registration order until one matches. The first match wins.

If no resource or template matches the requested URI, the client receives a JSON-RPC `Invalid params` error.

## Mid-execution interaction (progress and elicitation)

A resource (or resource-template) handler may declare an
`McpResourceContext` parameter to report progress or elicit input while it
runs — the same surface tool handlers get, scoped to `resources/read`
(ADR-0025, ADR-0024).

```java
@McpResource(uri = "report://latest", mimeType = "application/pdf")
public ReadResourceResult latestReport(McpResourceContext ctx) {
    var p = ctx.countingProgress(3L);
    p.emit("querying");
    p.emit("rendering");
    p.emit("encoding");
    return ReadResourceResult.ofBlob("report://latest", "application/pdf", bytes);
}
```

See the [interactive tools guide](interactive-tools.md) for the full
progress and elicitation API and the replay/idempotency contract.

## Contributing resources programmatically (`ResourceContributor`)

Not every resource comes from an annotated method. Any Spring bean that
implements **`ResourceContributor`** can supply resources directly, and mocapi
merges them with the annotation-scanned ones when it builds the resource service
at startup (ADR-0035). The `@McpResource` / `@McpResourceTemplate` scan is itself
just the built-in, primary contributor — yours is a peer.

Reach for it to register resources you can't (or don't want to) write out as
methods — generated content, a list pulled from config or an external source, or
a whole family of URIs assembled at startup:

```java
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ResourceContributor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StatusResources implements ResourceContributor {

    @Override
    public List<ReadResourceHandler> resources() {
        return List.of(
            new ReadResourceHandler(
                new Resource("status://live", "Live status", "Current service status", "application/json"),
                List.of(), // guards — empty means public
                () -> ReadResourceResult.ofText("status://live", "application/json", currentStatusJson())));
    }
}
```

Each handler pairs a descriptor (`Resource` for a fixed URI, `ResourceTemplate`
for a template) with a **reader**: a `Supplier<ReadResourceResult>`
(`ResourceReader`) for a fixed URI, or a `Function<Map<String,String>,
ReadResourceResult>` (`ResourceTemplateReader`) for a template. The reader runs
per `resources/read`, so per-request/dynamic content is inherent.

Two things to know:

- **Construction-time only.** Contributors are collected once at startup — there
  is no runtime registration (and no concurrency to reason about).
- **Contributed resources are public and un-observed.** A reader-only handler
  carries an empty `guards` list and no interceptor chain. If a resource needs
  per-handler authorization, observability, or validation, declare it as a
  `@McpResource` **method** instead and reach your logic by reference — that
  routes it through the full handler chain.

This is the same seam MCP Apps' serve-mode uses to contribute `ui://` bundles
without a resource method ([MCP Apps guide](apps.md)).
