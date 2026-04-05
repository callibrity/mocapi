# Final verification and cleanup

## What to build

Verify the entire upgrade is complete and clean up artifacts from the migration process.

### Full build verification

Run `mvn clean verify` and confirm the entire build passes including:
- Spotless formatting check (validate phase)
- License header check (validate phase)
- Compilation (all modules)
- Unit tests (all modules)
- Integration tests (mocapi-example)

### Stale reference checks

Search for and fix any remaining references to the old state:
- `grep -r "ripcurl" --include="*.java" --include="*.xml" --include="*.properties" .` — must return no matches
- `grep -r "com.fasterxml.jackson" --include="*.java" .` — must return no matches
- `grep -r "JsonRpcService\|JsonRpcInvalidParams\|JsonRpcInternalError\|JsonRpcException\|LazyInitializer" --include="*.java" .` — must return no matches (old ripcurl types)
- `grep -r "spring-boot-starter-web\"" --include="*.xml" .` — must return no matches (renamed to webmvc)

### README.md update

- Update the endpoint property documentation from `ripcurl.endpoint` to `mocapi.endpoint`
- Remove any references to RipCurl
- Update the MCP spec version link if needed
- Update any dependency version examples to reflect Spring Boot 4.0.5

### Cleanup files

- Delete `CODE_REVIEW.md` — all issues resolved
- Delete `PRD.example.md` — template no longer needed
- Delete `docs/plans/2026-04-05-java25-spring-boot-ripcurl-removal.md` — plan executed

### Dependency tree audit

Run `mvn dependency:tree` on each module and verify:
- No ripcurl artifacts appear anywhere in the tree
- No unexpected Jackson 2.x artifacts on the classpath
- No duplicate or conflicting versions

## Acceptance criteria

- [ ] `mvn clean verify` passes with zero warnings from Spotless or license plugin
- [ ] No `ripcurl` references in any Java, XML, or properties file
- [ ] No `com.fasterxml.jackson` imports in any Java file
- [ ] No old ripcurl type names (`JsonRpcService`, `JsonRpcInvalidParamsException`, etc.) in any Java file
- [ ] No `spring-boot-starter-web` (non-webmvc) in any POM
- [ ] `README.md` references `mocapi.endpoint`, not `ripcurl.endpoint`
- [ ] `CODE_REVIEW.md` is deleted
- [ ] `PRD.example.md` is deleted
- [ ] `mvn dependency:tree` shows no ripcurl artifacts and no Jackson 2.x artifacts
- [ ] All integration tests pass

## Implementation notes

- This spec depends on all previous specs (001-005) being complete.
- If any stale reference check fails, fix it before proceeding.
- The README update should be minimal — just fix what's stale, don't rewrite.
- Run the dependency tree check on `mocapi-example` since it transitively pulls everything.
