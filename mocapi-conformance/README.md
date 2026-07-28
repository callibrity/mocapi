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

> **Pending regeneration.** The numbers below are from the RC-era draft run
> (`--suite draft`, 2026-06-12) and are stale relative to both the final-spec
> `--spec-version 2026-07-28` invocation above and the `McpElicitor` SPI landing
> (ADR-0024). Re-run and reconcile before the 1.0.0 release. Treat the table
> below as indicative only.

Last RC-era run (`@0.2.0-alpha.2 --suite draft`, 2026-06-12): **51 checks
passed, 5 scenarios failed — all five explained and baselined** in
`conformance-expected-failures.yaml`.

### Passing scenarios

| Scenario | Checks | Driven by |
|---|---|---|
| `server-stateless` | 17 | — (protocol-level: no sessions, discover, envelope validation) |
| `http-header-validation` | 13 | — (protocol-level: Mcp-Method/Mcp-Name/MCP-Protocol-Version) |
| `caching` | 7 | — (protocol-level: ttlMs/cacheScope on cacheable results) |
| `sep-2164-resource-not-found` | 2 | — (protocol-level: -32602) |
| `completion-complete`, `tools-list`, `resources-*`, `prompts-*`, `dns-rebinding-protection`, `json-schema-2020-12`, `server-sse-multiple-streams`, `tools-call-*` | — | carried over from the 2025-11-25 set (sampling/logging tools removed) |
| `input-required-result-request-state` | 2 | `test_input_required_result_request_state` |
| `input-required-result-multi-round` | 3 | `test_input_required_result_multi_round` |
| `input-required-result-result-type` | 1 | `test_input_required_result_elicitation` |
| `input-required-result-tampered-state` | 1 | `test_input_required_result_tampered_state` |
| `input-required-result-unsupported-methods` | 1 | — (engine rejection matrix) |
| `input-required-result-validate-input` | 2 | — (engine rejection matrix) |
| `input-required-result-non-tool-request` | 2 | `test_input_required_result_prompt` (prompt-side elicitation, ADR-0024) |

### Baselined failures (`conformance-expected-failures.yaml`)

| Scenario | Why |
|---|---|
| `input-required-result-basic-sampling` | mocapi emits no sampling input requests — deprecated by SEP-2577, declined in [ADR-0022](../docs/adr/0022-2026-07-28-features-not-implemented.md). |
| `input-required-result-basic-list-roots` | Same for roots. |
| `input-required-result-multiple-input-requests` | Requires elicitation + sampling + roots requests in one result. |
| `input-required-result-capability-check` | Asserts sampling-only `inputRequests` for a sampling-only client; mocapi correctly completes without input requests instead. |
| `input-required-result-basic-elicitation` | Suite over-constraint: requires the literal `inputRequests` key `"user_name"`, but the spec says keys are server-assigned (mocapi assigns `elicit-<ordinal>`). Worth filing as suite feedback; the same tool passes `input-required-result-result-type`. |

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
