# Make @ToolService a Spring stereotype annotation

## What to build

Annotate `@ToolService` with `@Component` so it acts as a Spring stereotype annotation,
just like `@Service`, `@Repository`, and `@Controller`. Users should not need to stack
`@Component` and `@ToolService` on their tool classes.

### Update @ToolService

**File:** `mocapi-tools/src/main/java/com/callibrity/mocapi/tools/annotation/ToolService.java`

Add `@Component` as a meta-annotation on `@ToolService`. This means any class annotated
with `@ToolService` is automatically a Spring-managed bean.

### Update example app

**File:** `mocapi-example/src/main/java/.../HelloTool.java`, `Rot13Tool.java`, etc.

Remove the redundant `@Component` annotation from all `@ToolService` classes in the
example app, since `@ToolService` now implies it.

### Update ToolServiceMcpToolProvider

**File:** `ToolServiceMcpToolProvider.java`

The provider currently discovers beans via `context.getBeansWithAnnotation(ToolService.class)`.
This should continue to work since `@ToolService` beans are now component-scanned
automatically. Verify no changes needed.

### Update README

Update the tool example in `README.md` to show `@ToolService` without `@Component`.

### Update tests

Update any test classes that create `@ToolService` beans to remove redundant `@Component`.

## Acceptance criteria

- [ ] `@ToolService` is meta-annotated with `@Component`
- [ ] No `@ToolService` class in the project also has `@Component`
- [ ] Example app tool classes use only `@ToolService` (no `@Component`)
- [ ] `README.md` example shows `@ToolService` without `@Component`
- [ ] `ToolServiceMcpToolProvider` still discovers all `@ToolService` beans
- [ ] `mvn verify` passes

## Implementation notes

- This requires `mocapi-tools` to depend on `spring-context` for the `@Component`
  annotation. Check if this is already available transitively. If not, add it as an
  optional dependency.
- The `@ToolService` annotation already has `@Inherited` (class-level, so it's
  meaningful). Keep that.
