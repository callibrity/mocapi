# Final verification and cleanup

## What to build

Verify the entire upgrade is complete and clean up migration artifacts.

### Full build verification

Run `mvn clean verify` and confirm the entire build passes including:
- Spotless formatting check (validate phase)
- License header check (validate phase)
- Compilation (all modules)
- Unit tests (all modules)
- Integration tests (mocapi-example)

### Stale reference checks

Search for and fix any remaining references to the old state:
- `grep -r "ripcurl" --include="*.java" --include="*.xml" --include="*.properties" .` — no matches
- `grep -r "com.fasterxml.jackson" --include="*.java" .` — no matches
- `grep -r "LazyInitializer" --include="*.java" .` — no matches
- `grep -r "spring-boot-starter-web\"" --include="*.xml" .` — no matches (renamed to webmvc)
- `grep -r "McpPrompt\|PromptService\|McpPromptsCapability" --include="*.java" .` — no matches
- `grep -r "mocapi-prompts" --include="*.xml" .` — no matches

### Verify module list

Confirm the parent `pom.xml` modules list does NOT include `mocapi-prompts`.

### README.md update

- Update endpoint property from `ripcurl.endpoint` to `mocapi.endpoint`
- Remove all prompts documentation (Creating MCP Prompts section, @PromptService, @Prompt)
- Remove any references to RipCurl
- Verify the MCP spec version link

### Cleanup files

- Delete `CODE_REVIEW.md` — issues resolved or no longer applicable
- Delete `PRD.example.md` — template no longer needed
- Delete `docs/plans/2026-04-05-java25-spring-boot-ripcurl-removal.md` — plan executed

### Dependency tree audit

Run `mvn dependency:tree` on `mocapi-example` and verify:
- No ripcurl artifacts
- No Jackson 2.x artifacts
- No prompts module artifacts

## Acceptance criteria

- [ ] `mvn clean verify` passes
- [ ] No stale references to ripcurl, old Jackson, prompts, or old starter names
- [ ] `README.md` is updated (no prompts, no ripcurl, correct endpoint property)
- [ ] `CODE_REVIEW.md` is deleted
- [ ] `PRD.example.md` is deleted
- [ ] `mvn dependency:tree -pl mocapi-example` shows no ripcurl or Jackson 2.x artifacts
- [ ] Parent `pom.xml` does not list `mocapi-prompts` as a module

## Implementation notes

- This spec depends on all previous specs (001-005) being complete.
- The README update should be minimal — just remove stale content.
- If any stale reference check fails, fix it before proceeding.
