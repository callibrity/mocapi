# Arbitrary choice values and remove TitledValue

## What to build

Remove `TitledValue` record. Add `choose` and `chooseMany` overloads that
accept arbitrary objects with value/title extraction functions.

### Remove TitledValue

Delete `TitledValue` record. It's unnecessary now that enums use
`name()`/`toString()` directly and the new overloads handle arbitrary objects.

### New choose overloads

```java
<T> choose(String name, List<T> items, Function<T,String> valueFn, Function<T,String> titleFn)
<T> choose(String name, List<T> items, Function<T,String> valueFn)  // toString() for title
```

Generates `oneOf` with `const`/`title` from the items list.

### New chooseMany overloads

```java
<T> chooseMany(String name, List<T> items, Function<T,String> valueFn, Function<T,String> titleFn)
<T> chooseMany(String name, List<T> items, Function<T,String> valueFn)  // toString() for title
```

### Result getters

For the arbitrary choice case, the caller can't get back `T` — they get the
raw string value:

- `getChoice(name)` — returns String (the `const` value)
- `getChoices(name)` — returns `List<String>` for multi-select

The enum overloads stay:
- `getChoice(name, Class<E>)` — returns enum via `Enum.valueOf()`
- `getChoices(name, Class<E>)` — returns `List<E>`

## Acceptance criteria

- [ ] `TitledValue` record deleted
- [ ] `choose(name, items, valueFn, titleFn)` generates `oneOf`/`const`/`title`
- [ ] `choose(name, items, valueFn)` uses `toString()` for title
- [ ] `chooseMany` equivalents for both
- [ ] `getChoice(name)` returns String for arbitrary choices
- [ ] `getChoices(name)` returns `List<String>` for arbitrary multi-select
- [ ] Enum `choose`/`getChoice` overloads unchanged
- [ ] All tests pass
- [ ] `mvn verify` passes
