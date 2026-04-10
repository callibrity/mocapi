# Rename elicitation builders, collapse wrapper, unify titled/untitled enum builders

## What to build

After spec 102 lands, the elicitation builders in
`mocapi-core/.../stream/elicitation/` produce model schema records
instead of core-local duplicates — but their class names still
reference the old "property" terminology and the top-level orchestrator
still carries an `ElicitationSchema` wrapper between the builder and
the model's `RequestedSchema`. After spec 103 lands, the model has
sealed intermediate interfaces (`SingleSelectEnumSchema`,
`MultiSelectEnumSchema`, `EnumSchema`) that the builders can return as
precisely typed results.

This spec tightens the builder layer in three moves:

### Move 1 — Rename every builder to its schema name

The class names align 1:1 with the model schema type they produce (for
primitives) or the sealed category they produce (for enums):

| Current | New |
|---|---|
| `StringPropertyBuilder` | `StringSchemaBuilder` |
| `IntegerPropertyBuilder` | `IntegerSchemaBuilder` |
| `NumberPropertyBuilder` | `NumberSchemaBuilder` |
| `BooleanPropertyBuilder` | `BooleanSchemaBuilder` |
| `ChooseOneBuilder<T>` | `SingleSelectEnumSchemaBuilder<T>` |
| `ChooseManyBuilder<T>` | `MultiSelectEnumSchemaBuilder<T>` |
| `ChooseLegacyBuilder` | `LegacyTitledEnumSchemaBuilder` |
| `ElicitationSchemaBuilder` (top level) | `RequestedSchemaBuilder` |

Both `IntegerSchemaBuilder` and `NumberSchemaBuilder` produce a
`com.callibrity.mocapi.model.NumberSchema` — the former sets
`type = "integer"`, the latter sets `type = "number"`. They remain
distinct classes so the DSL call at the use site (`schema.integer(...)`
vs `schema.number(...)`) maps onto a builder whose name matches what
the tool author thinks they're building.

### Move 2 — Delete the `ElicitationSchema` wrapper

Today the flow is two-step:

```java
ElicitationSchemaBuilder builder = ElicitationSchema.builder();
schema.accept(builder);
RequestedSchema requested = builder.build().toRequestedSchema();
//                                  ^^^^^^^^ intermediate ElicitationSchema
```

Collapse it to one step:

```java
RequestedSchemaBuilder builder = new RequestedSchemaBuilder();
schema.accept(builder);
RequestedSchema requested = builder.build();   // returns RequestedSchema directly
```

- Delete `ElicitationSchema.java` (the wrapper class) entirely.
- `RequestedSchemaBuilder` holds the internal state directly
  (`LinkedHashMap<String, PrimitiveSchemaDefinition> properties` +
  `List<String> requiredNames`) and its `build()` returns a model
  `RequestedSchema` from those fields.
- Update `DefaultMcpStreamContext.elicit(...)` to use the new
  `RequestedSchemaBuilder` instantiation and the simplified flow.
- `RequestedSchemaBuilder` does NOT get a static `builder()` factory
  on `RequestedSchema` — that would make `mocapi-model` depend on
  `mocapi-core`. Tool authors never instantiate it directly; the
  framework does, inside `DefaultMcpStreamContext.elicit`.

### Move 3 — Unify the enum builders with an opt-in `titled(...)` method

`SingleSelectEnumSchemaBuilder<T>` and `MultiSelectEnumSchemaBuilder<T>`
currently behave as chameleons: the same builder class emits either an
`UntitledSingleSelectEnumSchema` or a `TitledSingleSelectEnumSchema`
depending on whether titles were supplied. Replace the implicit
decision with a single explicit opt-in method.

Both unified builders:

- Default to untitled. `build()` produces the untitled variant unless
  the tool author explicitly adds titles.
- Expose a new `titled(Function<T, String> titleFn)` fluent method
  that, when called, switches the builder into titled mode. The title
  function is applied per item at `build()` time to produce the
  `title` field of each `EnumOption`.
- Return a precise sealed type from `build()`:
  - `SingleSelectEnumSchemaBuilder<T>.build()` returns
    `com.callibrity.mocapi.model.SingleSelectEnumSchema` (the sealed
    interface added in spec 103). The concrete type at runtime is
    either `UntitledSingleSelectEnumSchema` or
    `TitledSingleSelectEnumSchema`, picked based on whether
    `titled(...)` was called.
  - `MultiSelectEnumSchemaBuilder<T>.build()` returns
    `com.callibrity.mocapi.model.MultiSelectEnumSchema` (sealed).
- Do NOT split into five concrete builder classes. One builder per
  category (single-select, multi-select) with an opt-in method is the
  clean answer; splitting was considered and rejected because the
  decision between titled and untitled has to live *somewhere* and a
  single well-named method call is the least awkward place to put it.

`LegacyTitledEnumSchemaBuilder` is untouched by Move 3 — the legacy
enum schema has a fixed shape (`enum` + `enumNames` arrays) with no
titled/untitled variants to collapse.

### Fluent method shape on `RequestedSchemaBuilder`

The three `choose(...)` overload families (raw strings, enum class,
arbitrary objects) and the three `chooseMany(...)` overload families
**keep their current names and parameter shapes**, but their
customizer variants now hand the caller a
`SingleSelectEnumSchemaBuilder<T>` / `MultiSelectEnumSchemaBuilder<T>`
on which `titled(...)` is the primary promotion path. Example:

```java
// Raw strings, untitled — no customizer needed
schema.choose("color", List.of("red", "green", "blue"));

// Raw strings, titled — customizer calls titled()
schema.choose("color", List.of("red", "green", "blue"),
    b -> b.titled(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)));

// Enum class, untitled (uses enum constant names)
schema.choose("priority", Priority.class);

// Enum class, titled
schema.choose("priority", Priority.class,
    b -> b.titled(e -> humanize(e.name())));

// Arbitrary objects, untitled
schema.choose("user", users, User::username);

// Arbitrary objects, titled (different function for display)
schema.choose("user", users, User::username,
    b -> b.titled(User::displayName));
```

The fluent method **names** on `RequestedSchemaBuilder` (`string`,
`integer`, `number`, `bool`, `choose`, `chooseMany`, `chooseLegacy`,
and their variant overloads) stay exactly as they are today — only
the underlying builder *class* names and the titled-vs-untitled
machinery change.

## Acceptance criteria

### Renames

- [ ] The following source files are renamed (class name + filename
      together) and no references to the old names remain anywhere
      in the reactor:
  - `StringPropertyBuilder` → `StringSchemaBuilder`
  - `IntegerPropertyBuilder` → `IntegerSchemaBuilder`
  - `NumberPropertyBuilder` → `NumberSchemaBuilder`
  - `BooleanPropertyBuilder` → `BooleanSchemaBuilder`
  - `ChooseOneBuilder` → `SingleSelectEnumSchemaBuilder`
  - `ChooseManyBuilder` → `MultiSelectEnumSchemaBuilder`
  - `ChooseLegacyBuilder` → `LegacyTitledEnumSchemaBuilder`
  - `ElicitationSchemaBuilder` → `RequestedSchemaBuilder`
- [ ] Javadoc on each renamed class is updated to reflect the new name
      and (where relevant) note the model schema type it produces.

### Wrapper deletion

- [ ] `ElicitationSchema.java` is deleted. No file or class named
      `ElicitationSchema` remains in `mocapi-core/src/main`.
- [ ] `RequestedSchemaBuilder.build()` returns
      `com.callibrity.mocapi.model.RequestedSchema` directly (no
      intermediate `toRequestedSchema()` step).
- [ ] `RequestedSchemaBuilder` is instantiated via
      `new RequestedSchemaBuilder()` (no static factory on the model
      record).
- [ ] `DefaultMcpStreamContext.elicit(...)` is updated to use the new
      `RequestedSchemaBuilder` and passes the resulting
      `RequestedSchema` directly to the elicit request construction
      path.

### Enum builder unification

- [ ] `SingleSelectEnumSchemaBuilder<T>` exposes a
      `titled(Function<T, String> titleFn)` method that returns `this`
      and stores the function. Calling it switches the builder into
      titled mode.
- [ ] `MultiSelectEnumSchemaBuilder<T>` exposes a
      `titled(Function<T, String> titleFn)` method with the same
      semantics.
- [ ] `SingleSelectEnumSchemaBuilder<T>.build()` declares a return
      type of `com.callibrity.mocapi.model.SingleSelectEnumSchema`
      (the sealed interface from spec 103). At runtime it returns
      either `UntitledSingleSelectEnumSchema` (titleFn == null) or
      `TitledSingleSelectEnumSchema` (titleFn != null).
- [ ] `MultiSelectEnumSchemaBuilder<T>.build()` declares a return
      type of `com.callibrity.mocapi.model.MultiSelectEnumSchema` and
      returns the untitled or titled concrete variant analogously.
- [ ] `LegacyTitledEnumSchemaBuilder.build()` continues to return
      `com.callibrity.mocapi.model.LegacyTitledEnumSchema` (concrete;
      no sealed category is needed for legacy).
- [ ] No runtime branching on "titled vs untitled" lives in
      `RequestedSchemaBuilder` — the `choose(...)` and
      `chooseMany(...)` fluent methods instantiate the unified builder
      and hand it to the optional customizer; the customizer (or its
      absence) decides via `titled(...)`.

### Fluent method surface (unchanged)

- [ ] `RequestedSchemaBuilder` exposes exactly the same fluent method
      names and overload groups as today's `ElicitationSchemaBuilder`:
      `string`, `integer`, `number`, `bool`, `choose`, `chooseMany`,
      `chooseLegacy`. Tool authors writing
      `schema.string("name", "desc").integer("age", "desc").choose(...)`
      see no source-level change.
- [ ] Customizer overloads hand out the renamed builder types
      (e.g., `Consumer<StringSchemaBuilder>`,
      `Consumer<SingleSelectEnumSchemaBuilder<E>>`). Tool authors using
      the customizer form get a compile error with a clear new type
      name after this migration.

### Tests

- [ ] Existing tests in `DefaultMcpStreamContextTest` for the builder
      path pass after updating only the builder class type
      references, not the fluent method calls themselves.
- [ ] New unit tests for `SingleSelectEnumSchemaBuilder` cover:
  - Default (no `titled(...)` call) produces an
    `UntitledSingleSelectEnumSchema` with the expected `values`.
  - Calling `titled(fn)` produces a `TitledSingleSelectEnumSchema`
    whose `oneOf` list has `EnumOption(value, title)` entries
    computed from the item list via `valueFn` and `titleFn`.
  - Enum-class factory path with and without `titled(...)`.
  - Raw-string factory path with and without `titled(...)`.
  - Arbitrary-object factory path with and without `titled(...)`.
  - `defaultValue(...)` customizer applies in both titled and
    untitled modes.
- [ ] Analogous unit tests for `MultiSelectEnumSchemaBuilder`.
- [ ] A `RequestedSchemaBuilder` round-trip test constructs a
      non-trivial schema via the fluent DSL (mix of primitives, one
      titled single-select, one untitled multi-select, one legacy) and
      asserts the serialized `RequestedSchema` JSON matches a golden
      fixture captured before this migration (byte-equivalent except
      for cosmetic field-ordering fixes, which should not apply).
- [ ] `mocapi-compat`'s `ConformanceTools` compiles with the renamed
      builder types. Conformance suite still passes 39/39.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Dependency order**: this spec must run **after** spec 102 (builders
  produce model types) and spec 103 (sealed intermediate interfaces
  exist). Spec 103 is the critical one — without the sealed
  `SingleSelectEnumSchema` / `MultiSelectEnumSchema` interfaces, the
  unified enum builders have no precise return type to declare (they'd
  fall back to the overly loose `PrimitiveSchemaDefinition`).
- **IDE refactor guidance**: every rename here is a mechanical
  "Refactor → Rename" in IntelliJ. Do the renames first, commit, then
  layer the unification work on top. Keeps the diff readable.
- **Recommended commit granularity** (for bisectability):
  1. Rename the four primitive builders + update call sites.
  2. Rename `ElicitationSchemaBuilder` → `RequestedSchemaBuilder` and
     update call sites (still uses the `ElicitationSchema` wrapper in
     this commit).
  3. Rename the three enum builders to their new names (still using
     the old chameleon logic, return type still loose).
  4. Tighten the enum builders' `build()` return types to
     `SingleSelectEnumSchema` / `MultiSelectEnumSchema` / concrete
     `LegacyTitledEnumSchema`.
  5. Add the `titled(Function<T, String>)` method to the unified enum
     builders and refactor their internal logic so `titleFn == null`
     drives the untitled-vs-titled decision at `build()` time.
  6. Delete the `ElicitationSchema` wrapper, update
     `RequestedSchemaBuilder.build()` to return `RequestedSchema`
     directly, and update `DefaultMcpStreamContext.elicit`.
  7. Delete any now-unused factory methods on
     `SingleSelectEnumSchemaBuilder` / `MultiSelectEnumSchemaBuilder`
     that only existed to support the old chameleon logic (e.g., a
     factory that took both value and title functions when the
     unified builder now expects the title function to arrive via
     `titled(...)`).
- **Fluent method overload count**: the `choose(...)` fluent methods
  on `RequestedSchemaBuilder` should keep the same overload shape as
  today: a bare form (no customizer), a default-value form, and a
  customizer form. After the unification, the default-value form
  still works (defaultValue is a builder state independent of
  titled/untitled). The customizer form is where tool authors call
  `titled(...)` if they want it. Do not introduce new fluent method
  names like `chooseTitled` or `chooseUntitled` — the whole point is
  that titled-vs-untitled is a customizer concern, not a
  method-name concern.
- **Factory methods on the unified enum builders**: today's
  `ChooseOneBuilder.from(List<String>)`,
  `ChooseOneBuilder.from(List<T>, Function<T, String>)`, and
  `ChooseOneBuilder.fromEnum(Class<E>)` factories stay but return
  `SingleSelectEnumSchemaBuilder` instances pre-populated with the
  items and `valueFn`. None of them should take a title function as
  a constructor parameter — titles are only added via `titled(...)`
  after construction.
- **Generic type of `SingleSelectEnumSchemaBuilder<T>`**: `T` is the
  item type. For raw-string factories it's `T = String`. For
  enum-class factories it's `T = E extends Enum<E>`. For
  arbitrary-object factories it's the caller's custom type. The
  `titled(Function<T, String>)` method's type parameter aligns
  automatically.
- **Legacy enum builder is untouched** aside from the rename. Its
  `build()` still returns a concrete `LegacyTitledEnumSchema` because
  there's only one concrete type in that branch of the sealed
  hierarchy.
- **Don't touch** `ElicitationAction` (the core enum with `ACCEPT`,
  `DECLINE`, `CANCEL`) or `ElicitationResult` (the builder-based
  result facade) or any elicitation exception classes. They're
  orthogonal to this spec's scope.
- **Don't touch** the model module in this spec. All changes are
  confined to `mocapi-core/src/main/java/com/callibrity/mocapi/stream/elicitation/`,
  `mocapi-core/src/main/java/com/callibrity/mocapi/stream/DefaultMcpStreamContext.java`,
  and their corresponding tests.
- **Why we're not splitting into five concrete enum builders**: we
  considered splitting `SingleSelectEnumSchemaBuilder<T>` into
  `UntitledSingleSelectEnumSchemaBuilder<T>` and
  `TitledSingleSelectEnumSchemaBuilder<T>` (same for multi-select).
  That approach eliminates runtime branching but requires either
  duplicating every customizer method across two builder classes or
  pushing the decision up to the `RequestedSchemaBuilder` fluent
  method overloads — which just moves the chameleon logic into
  method-selection logic. The unified-builder + `titled(...)` opt-in
  is strictly simpler: one builder class per category, one sealed
  return type, one explicit opt-in method, and the branching is a
  single `if (titleFn == null)` check inside `build()`.
