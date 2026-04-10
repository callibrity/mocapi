# Split elicitation and sampling timeouts into distinct configuration properties

## What to build

`DefaultMcpStreamContext` currently uses a single `elicitationTimeout`
field (a `Duration`) for **both** elicitation and sampling flows. The
field was originally added for elicitation and later reused by
sampling as a matter of convenience. That conflation is a
pre-existing quirk worth cleaning up, for several reasons:

1. **Semantic mismatch**: elicitation waits for a *human* to fill in
   a form — typical timeouts are measured in minutes. Sampling waits
   for an *LLM* to generate a response — typical timeouts are
   measured in seconds to tens of seconds. Forcing a single timeout
   value means operators have to pick the max of the two, which
   over-waits on sampling failures and under-waits on long-running
   elicitation dialogs.
2. **Configuration clarity**: operators configuring mocapi will
   expect two knobs, one per flow. A single `elicitationTimeout`
   property that also governs sampling is surprising.
3. **Observability**: if either flow times out, it's currently
   impossible to distinguish "the user walked away from the elicit
   form" from "the LLM took too long" just from the timeout setting.

### Changes

**`DefaultMcpStreamContext`**:
- Add a second `Duration samplingTimeout` field alongside the
  existing `Duration elicitationTimeout`.
- Update the constructor to accept both as separate parameters.
- `sample()` uses `samplingTimeout`. `sendElicitationAndWait()` uses
  `elicitationTimeout`. The shared mailbox-polling helper from spec
  110 (if it's landed) already takes the timeout as a parameter, so
  each caller just passes its own field.

**`MocapiProperties`** (or the equivalent `@ConfigurationProperties`
bean that feeds the stream context constructor):
- Rename the existing `elicitation.timeout` (or whatever it's
  currently called) property path into a nested structure:
  - `mocapi.elicitation.timeout` (existing) — stays as-is; default
    unchanged.
  - `mocapi.sampling.timeout` (new) — default to `30s` (a
    reasonable LLM response timeout).
- Emit `@ConfigurationPropertiesBinding` metadata so both properties
  show up in Spring Boot auto-completion.

**Auto-configuration** (`MocapiAutoConfiguration` or similar):
- Wire both timeouts into the `DefaultMcpStreamContext`-producing
  factory method. The factory method (or whatever creates the
  stream context) must pass both `Duration` values into the
  constructor.

## Acceptance criteria

### Stream context

- [ ] `DefaultMcpStreamContext` has two distinct timeout fields:
      `Duration elicitationTimeout` and `Duration samplingTimeout`.
- [ ] The constructor signature includes both (replacing the single
      parameter that was there before). The order is
      `elicitationTimeout` first, `samplingTimeout` second, for
      consistency with the existing parameter position.
- [ ] `sample()` (and anything it delegates to) uses
      `samplingTimeout`.
- [ ] `sendElicitationAndWait()` uses `elicitationTimeout`.
- [ ] No field or variable named `elicitationTimeout` is passed to
      sampling code, and vice versa. Grep the file to verify.

### Configuration

- [ ] A new Spring Boot property `mocapi.sampling.timeout` is
      defined and documented in `spring-configuration-metadata.json`
      (via the `@ConfigurationProperties` annotation processor) with
      a default of `30s`.
- [ ] The existing `mocapi.elicitation.timeout` property retains
      its default (whatever it was before — likely `5m` or similar;
      check the current config).
- [ ] The auto-configuration bean factory that creates the
      `DefaultMcpStreamContext` passes both timeouts into the
      constructor.

### Tests

- [ ] `DefaultMcpStreamContextTest` constructor calls for the
      context helper are updated to pass both timeouts. Most tests
      don't care about the values — they can pass the same
      `Duration.ofSeconds(5)` for both. The few tests that
      specifically test timeout behavior should pass distinct
      values so they can assert that the right timeout drove the
      behavior.
- [ ] A new test specifically verifies that `sample()` uses the
      `samplingTimeout`: construct a context with
      `samplingTimeout=100ms`, stub the mailbox to never deliver,
      assert that `sample()` times out after ~100ms and not after
      the `elicitationTimeout`.
- [ ] An analogous new test verifies that `sendElicitationAndWait()`
      (exercised via `ctx.elicit(...)`) uses the
      `elicitationTimeout`.
- [ ] Any existing auto-configuration integration test (spring
      context test) that verifies the timeout property is picked
      up is extended to verify both properties.
- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.

## Implementation notes

- **Dependency**: this spec is independent of the spec 110 helper
  extraction. Whichever lands first, the other slots in cleanly.
  If 110 lands first, the helper already takes a `Duration` parameter
  — just call it with the right field. If 111 lands first, the
  inline code in `sample()` and `sendElicitationAndWait()` each
  reference a different field directly.
- **Property defaults** — pick sensible defaults:
  - `mocapi.elicitation.timeout = 5m` (elicitation waits for a
    human — minutes is reasonable; check what the current single
    timeout default is)
  - `mocapi.sampling.timeout = 30s` (LLM response — tens of seconds
    is reasonable)
  If the current single-field default is different from `5m`, keep
  that as the elicitation default to preserve behavior, and pick a
  smaller value (say half) for the sampling default.
- **Breaking change vs backward-compat**: configuration changes
  should ideally be backward-compatible. If any existing deployment
  has `mocapi.elicitation.timeout=2m` in their `application.yml`,
  they should continue to see the same elicitation behavior after
  upgrading to this version, and they'll get the default 30s
  sampling timeout unless they set it explicitly. That's a clean
  upgrade path.
- **Do NOT** introduce a shared `mocapi.timeout` parent property or
  anything like that — the whole point is to make them independent.
- **Naming**: stick with `mocapi.sampling.timeout` (not
  `mocapi.sample.timeout`). The MCP spec uses the word "sampling"
  in the method name (`sampling/createMessage`), so consistency
  matters.
- **Javadoc** on both fields in `DefaultMcpStreamContext` should
  explain what each one controls and why they're separate.
- **Commit suggested as a single focused change** — small enough
  that splitting doesn't help bisectability.
