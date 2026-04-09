# Always use oneOf/const/title for enums

## What to build

Simplify enum schema generation. Always use `oneOf` with `const`/`title` for
single-select and `anyOf` with `const`/`title` for multi-select. Remove the
code path that detects whether `toString()` differs from `name()` and
conditionally generates plain `enum` arrays.

For every enum constant: `const` = `name()`, `title` = `toString()`.
If they happen to be the same, that's fine.

## Acceptance criteria

- [ ] `choose()` always generates `oneOf` with `const`/`title`
- [ ] `chooseMany()` always generates array with `anyOf` containing `const`/`title`
- [ ] No conditional logic checking `toString()` vs `name()`
- [ ] All tests pass
- [ ] `mvn verify` passes
