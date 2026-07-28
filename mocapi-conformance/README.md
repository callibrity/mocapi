# mocapi-conformance

MCP protocol conformance server for Mocapi.

This module is a runnable Spring Boot application wired up with a set
of tools, prompts, and resources that cover every scenario in the
official [`@modelcontextprotocol/conformance`](https://www.npmjs.com/package/@modelcontextprotocol/conformance)
npx tool. It is not published to Maven Central — its only purpose is to
serve as a target for conformance runs against a known-good set of
annotated beans.

## Running the conformance suite

### 1. Start the conformance server

```bash
mvn spring-boot:run -pl mocapi-conformance
```

The server starts on port 8081 with the MCP endpoint at `/mcp`.

### 2. Run the 2026-07-28 suite

The 2026-07-28 spec is final; the conformance tool targets it as a first-class
dated version via `--spec-version 2026-07-28` (which drives the stateless
lifecycle), replacing the RC-era `--suite draft` track that sent the
`DRAFT-2026-v1` sentinel (removed in [ADR-0027](../docs/adr/0027-remove-draft-2026-v1-alias.md)).

```bash
npx @modelcontextprotocol/conformance@0.2.0-alpha.10 server \
  --url http://localhost:8081/mcp --suite active --spec-version 2026-07-28 \
  --expected-failures mocapi-conformance/conformance-expected-failures.yaml
```

## Current conformance status (2026-07-28)

Last full run (`@0.2.0-alpha.10 --suite all --spec-version 2026-07-28`, 2026-07-28):
**79 passed, 13 failed — all 13 baselined** in `conformance-expected-failures.yaml`.
Every baselined failure is a deliberate omission or a suite defect, **not** a
protocol-correctness gap; mocapi passes every check that tests actual protocol
behaviour (including the full MRTR/elicitation surface).

### Baselined failures (`conformance-expected-failures.yaml`)

| Scenario / check | # | Why |
|---|---|---|
| `server-stateless` → `sep-2575-server-rejects-undeclared-capability`, `…-missing-capability-http-400` | 2 | `test_missing_capability` requires the client to declare `sampling` and expects `-32021`. mocapi has no public API to require an arbitrary client capability, and sampling is deprecated (SEP-2577). Its `-32021` **is** implemented and passing for elicitation — only the generic sampling probe is unexpressible. |
| `http-custom-header-server-validation` | 5 | SEP-2243 `x-mcp-header` — declined **on principle** ([ADR-0028](../docs/adr/0028-decline-sep-2243-custom-parameter-headers.md)): HTTP-only, breaks the transport-agnostic server contract. All five `NotTestable` (no `x-mcp-header` tool). |
| `json-schema-2020-12` | 1 | Author-supplied rich 2020-12 `inputSchema`; mocapi generates tool schemas from Java types (ADR-0016), no raw-schema escape hatch. `NotTestable`, not a mangling bug. |
| `input-required-result-basic-sampling`, `-basic-list-roots`, `-multiple-input-requests`, `-capability-check` | 4 | Sampling & roots — deprecated by SEP-2577, declined ([ADR-0022](../docs/adr/0022-2026-07-28-features-not-implemented.md)). |
| `input-required-result-basic-elicitation` | 1 | Suite over-constraint: hard-codes the `inputRequests` key `"user_name"`, but the spec says keys are server-assigned (mocapi assigns `elicit-<ordinal>`). Spec-correct; the same tool passes `input-required-result-result-type`. Filed as suite feedback. |

Alpha-suite caveat: with `--expected-failures`, scenarios that report
"0 passed, 0 failed" are sometimes misreported as unexpected failures —
re-run without the baseline to confirm the true summary.

## Adding new conformance scenarios

1. Add a method to the appropriate `Compatibility*.java` bean (tool, prompt, or resource) with the right annotation.
2. Add Javadoc referencing the npx scenario name and linking to the relevant MCP spec section:
   ```java
   /**
    * Conformance tool for the {@code scenario-name} scenario.
    *
    * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools Specification</a>
    */
   ```
3. Re-run the npx conformance suite locally to confirm it passes and add the scenario to the table above.
