# Extract elicitation classes into their own package

## What to build

Move all elicitation-related classes from `com.callibrity.mocapi.stream` into
a new `com.callibrity.mocapi.stream.elicitation` package. The `ElicitationSchema`
file is too large — split the constraint interfaces, constraint implementations,
and builder into separate files.

### New package: `com.callibrity.mocapi.stream.elicitation`

Move these files:
- `ElicitationSchema.java` → split (see below)
- `ElicitationSchemaValidator.java`
- `ElicitationResult.java`
- `BeanElicitationResult.java`
- `ElicitationAction.java`

### Split ElicitationSchema.java

Extract into separate files:

- `ElicitationSchema.java` — just the schema record (properties, required, toObjectNode)
- `ElicitationSchemaBuilder.java` — the Builder class (currently inner class)
- `StringConstraints.java` — interface
- `NumericConstraints.java` — interface
- `BooleanConstraints.java` — interface
- `ChoiceConstraints.java` — interface
- `MultiChoiceConstraints.java` — interface

The constraint implementation classes can stay package-private inside the builder
file or be separate package-private files.

### Update references

Update all imports in:
- `DefaultMcpStreamContext`
- `McpStreamContext`
- Conformance tools
- Test files

## Acceptance criteria

- [ ] `com.callibrity.mocapi.stream.elicitation` package created
- [ ] All elicitation classes moved to new package
- [ ] `ElicitationSchema` split into schema + builder + constraint interfaces
- [ ] Each constraint interface in its own file
- [ ] All imports updated
- [ ] All tests pass
- [ ] `mvn verify` passes
