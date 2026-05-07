# Externalizing Annotation Metadata

Mocapi annotations carry strings that describe each tool, prompt, or resource to an MCP client: `name`, `title`, `description`, `uri`, `uriTemplate`, `mimeType`. These strings default to literal values on the annotation, but they can also use Spring's `${...}` property placeholder syntax so that long descriptions, URIs, and names can live in `application.yml` or `application.properties` instead of being inlined on the annotation.

## When to use it

Externalize annotation strings when:

- Descriptions are long enough that inlining them bloats the source file. MCP descriptions work best when they're rich — a sentence or two about what the tool does, when to use it, and what each argument means. That content doesn't belong wedged into a Java source file.
- The same tool ships in multiple environments with different URIs or mime types.
- You want to translate metadata or swap wording for different deployments without rebuilding.

Keep the annotation value literal when the string is short and stable (e.g. `name = "greet"`).

## Which fields support it

Every string attribute on `@McpTool`, `@McpPrompt`, `@McpResource`, and `@McpResourceTemplate` flows through the Spring environment:

| Annotation | Fields |
|---|---|
| `@McpTool` | `name`, `title`, `description` |
| `@McpPrompt` | `name`, `title`, `description` |
| `@McpResource` | `uri`, `name`, `title`, `description`, `mimeType` |
| `@McpResourceTemplate` | `uriTemplate`, `name`, `title`, `description`, `mimeType` |

## Example

```java
@Component
public class SearchTool {

    @McpTool(
        name = "${tools.search.name}",
        description = "${tools.search.description}")
    public SearchResult search(String query) {
        // ...
    }
}
```

```yaml
# application.yml
tools:
  search:
    name: catalog-search
    description: |
      Search the product catalog by free-text query. Returns up to 20
      results ranked by relevance. Use this when the user asks "do you
      have ...", "show me ...", or similar catalog-discovery queries.
      Not suitable for lookups by SKU — use `catalog-lookup` for that.
```

At startup the resolved values become the tool's registered metadata — MCP clients see them on `tools/list` exactly as if you had written the strings inline.

## Fail-fast on missing properties

Placeholder resolution runs once, at registration time, during the provider bean's `@PostConstruct`. If a property referenced by `${...}` is not defined, Spring raises a `PlaceholderResolutionException` and the application context fails to start. You will never see a literal `${...}` string flow through to an MCP response.

This is deliberate: MCP clients make routing decisions based on tool names and descriptions, so a typo in a property key is a bug that deserves a failed startup, not silent degradation.

## Default resolution

If the annotation field is left at its default empty string, the framework generates a fallback (the same behavior as before placeholder support). Placeholders only apply when you supply a `${...}` expression.

## SpEL

The resolver is backed by `ConfigurableBeanFactory::resolveEmbeddedValue`, so both `${...}` property placeholders and `#{...}` SpEL expressions work. Prefer `${...}` for straightforward externalization; reach for `#{...}` only when you genuinely need an expression.
