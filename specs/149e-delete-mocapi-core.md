# Delete mocapi-core

## What to build

Remove `mocapi-core` from the reactor. Everything it provided is
now in `mocapi-server` and `mocapi-transport-streamable-http`.

### Steps

1. Remove `mocapi-core` from the starter pom.
2. Remove `<module>mocapi-core</module>` from parent pom.
3. Delete the `mocapi-core/` directory.
4. Run `git grep mocapi-core` and fix any remaining references
   outside of `specs/done/`.
5. Update PRD.md and README.md if they reference mocapi-core.

## Acceptance criteria

- [ ] `mocapi-core` directory is deleted.
- [ ] Parent pom no longer lists it.
- [ ] Starter no longer depends on it.
- [ ] `git grep "mocapi-core"` returns zero outside `specs/done/`.
- [ ] `mvn clean verify` passes.
- [ ] MCP conformance suite: 39/39.
