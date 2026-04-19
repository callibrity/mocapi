# Rename method annotations to @McpTool / @McpPrompt / @McpResource / @McpResourceTemplate

## What to build

Rename the four method-level handler annotations to drop the
`Method` suffix and add the `Mcp` domain prefix. Leaves the
class-level `@ToolService` / `@PromptService` / `@ResourceService`
annotations alone — those aren't colliding with anything and their
meaning ("this class hosts handlers") is already clear.

This rename is purely cosmetic. No behavior change. Runs *after*
specs 170–174 so the old `McpTool` / `McpPrompt` / `McpResource` /
`McpResourceTemplate` interface names are out of the way.

| Before | After |
|---|---|
| `@com.callibrity.mocapi.api.tools.ToolMethod` | `@com.callibrity.mocapi.api.tools.McpTool` |
| `@com.callibrity.mocapi.api.prompts.PromptMethod` | `@com.callibrity.mocapi.api.prompts.McpPrompt` |
| `@com.callibrity.mocapi.api.resources.ResourceMethod` | `@com.callibrity.mocapi.api.resources.McpResource` |
| `@com.callibrity.mocapi.api.resources.ResourceTemplateMethod` | `@com.callibrity.mocapi.api.resources.McpResourceTemplate` |

The `@ToolService` / `@PromptService` / `@ResourceService`
annotations stay untouched.

---

## File-level changes

### Rename annotations

- `mocapi-api/src/main/java/com/callibrity/mocapi/api/tools/ToolMethod.java`
  → `McpTool.java`. Update the declared type name inside the file
  too (not just the filename).
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/prompts/PromptMethod.java`
  → `McpPrompt.java`.
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/resources/ResourceMethod.java`
  → `McpResource.java`.
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/resources/ResourceTemplateMethod.java`
  → `McpResourceTemplate.java`.

### Update all usage sites

Every call-site reference to the old names gets updated. At minimum:

- `mocapi-server` handler discovery (the `CallToolHandlers` /
  `GetPromptHandlers` / `ReadResourceHandlers` /
  `ReadResourceTemplateHandlers` classes created in specs 170–173).
  Each `getMethodsListWithAnnotation(..., ToolMethod.class)` →
  `..., McpTool.class`. Likewise for the other three.
- Every test fixture that has `@ToolMethod` etc. on a method.
- `examples/**` — the example apps use these annotations heavily.
- Javadoc references in `mocapi-api` classes that mention the old
  names (e.g. `McpToolContext` docs that reference `@ToolMethod`).
- Any AOT reflection hints that reference the annotation type
  (search `Runtime*Hints*` files).

### Internal helper renames

Classes / methods that include the old annotation name in their own
identifier (e.g. a helper called `ToolMethodValidator`) stay —
they're internal names and the `Method` there has a different sense.
Only rename what's actually the annotation type or an identifier
that's named *after* the annotation.

### Docs + README

- `README.md` — every `@ToolMethod` / `@PromptMethod` / `@ResourceMethod`
  / `@ResourceTemplateMethod` example becomes the new name.
- `docs/handlers.md` (the one spec 170–173 will have introduced) —
  same.
- `docs/observability-roadmap.md` — no changes expected but verify.

---

## Acceptance criteria

- [ ] Four annotation type files renamed (both filename and declared
      type).
- [ ] `grep -rn "@ToolMethod\|@PromptMethod\|@ResourceMethod\|@ResourceTemplateMethod"`
      returns no hits.
- [ ] `grep -rn "ToolMethod.class\|PromptMethod.class\|ResourceMethod.class\|ResourceTemplateMethod.class"`
      returns no hits.
- [ ] `grep -rn "import com.callibrity.mocapi.api.tools.ToolMethod\|import com.callibrity.mocapi.api.prompts.PromptMethod\|import com.callibrity.mocapi.api.resources.ResourceMethod\|import com.callibrity.mocapi.api.resources.ResourceTemplateMethod"`
      returns no hits.
- [ ] `mvn verify` + `mvn spotless:check` green on the whole reactor.
- [ ] Example apps build and run with the new annotation names.
- [ ] `@ToolService` / `@PromptService` / `@ResourceService` left
      untouched — verified by a grep showing they still exist.

## Docs

- [ ] `CHANGELOG.md` under `## [Unreleased]` / `### Breaking changes`:
      entry listing the four annotation renames. Include a one-line
      sed-style migration note (`sed -i '' 's/@ToolMethod/@McpTool/g'`
      etc.) for downstream projects.

## Commit

Suggested commit message:

```
Rename method annotations to @McpTool / @McpPrompt / @McpResource
/ @McpResourceTemplate

Drops the Method suffix (which was disambiguating from the now-
deleted McpTool/McpPrompt/... interfaces) and adds the Mcp domain
prefix. Class-level @ToolService / @PromptService / @ResourceService
left as-is — they don't collide with anything and their meaning is
already clear.

Purely cosmetic; no behavior change. Example apps, tests, README,
and docs all updated.

BREAKING: the four method annotations are renamed. Migration is a
find-and-replace of four tokens across any downstream project.
```

## Implementation notes

- Do not combine this rename with any behavior change. It's a pure
  name-shuffle.
- The `Mcp` prefix on the annotations is consistent with most of the
  rest of the mocapi-api namespace (`McpToolContext`, `McpLogger`,
  `McpSession`, etc.). One-off exceptions (`PromptArgument`,
  `ToolService`) stay as they are — those are outside this rename's
  scope.
- Check that GraalVM native-image hint registrations (in
  `AutoConfiguration.imports` or reflection hint classes) reference
  the new annotation types; AOT hints on the old names would silently
  drop reflection metadata at build time.
