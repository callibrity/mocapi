# Increase reactor code coverage toward 100%

## What to build

SonarCloud currently reports mocapi's reactor code coverage
at **82.5% overall** (85.3% line coverage, 69.9% branch
coverage). The project's stated goal is **100% coverage**.
This spec identifies the gap, prioritizes it by module, and
tracks the work to close it.

**Note**: 100% coverage is an aspirational goal, not a hard
requirement. Some code (defensive `null` checks, unreachable
catch blocks for "never happens" exceptions, trivial
getters/setters) is legitimately uncoverable without
contorting tests. The spec's real target is **every line of
meaningful logic is covered**, and any deliberately
uncovered line has a `// coverage: <reason>` comment.

### Current gap

From the latest Sonar snapshot:
- **Overall coverage**: 82.5%
- **Line coverage**: 85.3% (217 uncovered lines)
- **Branch coverage**: 69.9% (99 uncovered conditions)
- **Total LOC**: 5534

The branch coverage gap (69.9% vs 100%) is the biggest
concern — it means many `if`/`switch` paths have
single-branch coverage only. The line coverage gap (85.3%) is
smaller but still 217 lines of uncovered production code.

### Per-module audit

Run the SonarCloud API to get per-module coverage:

```bash
curl -s "https://sonarcloud.io/api/measures/component_tree?component=callibrity_mocapi&metricKeys=coverage,line_coverage,branch_coverage,uncovered_lines,uncovered_conditions&strategy=children" \
  | python3 -c "
import json, sys
data = json.load(sys.stdin)
for comp in data.get('components', []):
    name = comp.get('name', '?')
    measures = {m['metric']: m['value'] for m in comp.get('measures', [])}
    print(f\"{name:40}  cov={measures.get('coverage', '?'):>6}  line={measures.get('line_coverage', '?'):>6}  branch={measures.get('branch_coverage', '?'):>6}  uncov_lines={measures.get('uncovered_lines', '?'):>4}  uncov_cond={measures.get('uncovered_conditions', '?'):>3}\")
"
```

This produces a per-module breakdown that lets the implementer
prioritize. Expected outcome: some modules are already at
100% (mocapi-model thanks to spec 121), others have
significant gaps.

### Priority order for closing the gap

1. **Fix assertion-less tests first** (spec 139 — BLOCKERs).
   These are worse than uncovered code: they show up as
   "covered" while validating nothing. Close these before
   anything else.
2. **Branch coverage** (69.9% → 100%). Uncovered branches
   often reveal untested edge cases. Add tests for the
   untaken branches.
3. **Line coverage** (85.3% → 100%). Remaining uncovered
   lines are usually inside conditional blocks whose
   condition is never exercised by the test suite.
4. **mocapi-compat coverage**. The conformance tests cover
   end-to-end scenarios but may leave framework glue code
   uncovered. Add targeted unit tests for any glue not
   exercised by the conformance suite.

### Detecting uncoverable code

Some lines genuinely can't be covered by reasonable tests:
- **Defensive `null` checks** inside methods that are called
  only with non-null from within the framework
- **`default:` branches in exhaustive sealed switches** (Java
  requires them even if pattern-matching exhaustiveness makes
  them unreachable)
- **`private` constructors for utility classes** that exist
  only to prevent instantiation
- **`throw new AssertionError("unreachable")` lines** in
  method bodies that should never be reached at runtime

For each of these, add a line comment explaining why:

```java
// coverage: unreachable — sealed interface is exhaustively matched above
throw new AssertionError("unreachable");
```

And — only for genuinely unreachable lines — add JaCoCo
exclusion comments or configuration if JaCoCo doesn't
recognize them:

```java
// jacoco: exclude — private constructor to prevent instantiation
private MyUtility() {}
```

### Not "hacks to inflate the number"

Don't add tests that just call methods without asserting
anything (that's spec 139's BLOCKER). Don't add
`@Generated` annotations to skip coverage on legitimate
code. Don't add `@SuppressWarnings` to hide the issue.
**Real coverage comes from real tests of real behavior.**

## Acceptance criteria

- [ ] Per-module coverage audit runs and produces a table
      showing current coverage per module.
- [ ] For every module below 100%, a list of uncovered
      lines and conditions is produced (JaCoCo reports
      contain this data at
      `target/site/jacoco/jacoco.xml`; use it to generate
      a per-file gap report).
- [ ] For each uncovered line or condition:
  - **If it's real logic** — add a test that exercises it.
  - **If it's defensive/unreachable** — add a line comment
    explaining why, and add a `//jacoco:exclude` annotation
    or equivalent if coverage tooling doesn't skip it
    automatically.
  - **If it's dead code** — delete it. Don't cover code
    that shouldn't exist.
- [ ] After the work, running the Sonar scan shows:
  - Overall coverage ≥ **95%** (stretch goal: 100%)
  - Line coverage ≥ **98%**
  - Branch coverage ≥ **90%**
- [ ] If any module is still below those numbers, document
      the specific uncovered lines in a follow-up note with
      justification (why they can't be covered).
- [ ] `mvn verify` passes across the full reactor.
- [ ] No tests are added that lack meaningful assertions
      (S2699 must remain at zero after this spec).

## Implementation notes

- **This spec is broad and may span multiple iterations** of
  Ralph's loop. It's OK to split into `142a` / `142b` /
  etc. for specific modules if a single PR feels too large.
  One reasonable split:
  - **142a**: mocapi-core coverage gap
  - **142b**: mocapi-compat coverage gap
  - **142c**: mocapi-session-store-jdbc coverage gap
  Each focuses on one module.
- **Don't chase 100% for its own sake**. If you're 10 tests
  deep trying to cover a defensive null check on a method
  that's only called from one place with a non-null value,
  stop and add the `// coverage: defensive` comment. The
  test suite's purpose is validation, not percentage.
- **JaCoCo exclusion patterns**: mocapi's parent pom
  presumably has a `jacoco-maven-plugin` configuration. If
  exclusion patterns are needed for entire classes (e.g.,
  `*ExampleApplication` main-method classes), add them to
  the plugin config in one place rather than sprinkling
  `//jacoco:exclude` comments everywhere.
- **The branch coverage gap is the real work**. Line
  coverage at 85.3% is pretty good already; branch coverage
  at 69.9% means many conditionals have untested branches.
  Focus on writing tests that exercise the "unlikely" side
  of every `if` — error paths, timeout paths, malformed-
  input paths, etc.
- **Coordinate with spec 121** (model test coverage): spec
  121 specifically targeted `mocapi-model` with parameterized
  serialization tests. After spec 121 lands, re-run the
  per-module coverage audit. mocapi-model should already be
  at or near 100%. This spec's focus should shift to other
  modules.
- **Don't touch code that specs 118, 119, 120, 139 are
  already rewriting**. If Ralph's working on one of those
  specs and you're writing coverage tests for the same
  files, you'll have merge conflicts. Wait for those specs
  to land first.
- **Commit granularity**: per-module commits ideally. One
  big "add tests" commit spanning multiple modules is
  harder to review.
