# Mocapi Performance History

Append a row here after each soak. See
[`benchmarking.md`](./benchmarking.md) for the methodology. Keep
entries chronological (newest at top).

**Row format:** date · git SHA · stack summary · key numbers · notes.

---

## 2026-04-19 — post Methodical 0.7 / ripcurl 2.8 migration

- **Stack:** Methodical 0.7.0 (Central) / ripcurl 2.8.0-SNAPSHOT (local) / Spring Boot 4.0.5 / Micrometer 1.16.4 / Micrometer Tracing 1.6.4 / OpenTelemetry 1.55.0 / JDK 25 (Liberica)
- **Hardware:** ARM64 macOS, 700 MB heap, dev laptop
- **Config:** full observability, 100 % trace sampling, OTLP to Jaeger all-in-one, OTLP metrics export disabled
- **Load:** 16-way parallel synchronous loop, 180 s, `hello` + `rot-13-tool.encode` mix
- **Recorded baseline:**

| Metric | Value |
|---|---|
| Calls | 101,621 |
| Errors | 0 |
| Throughput | 564 req/s |
| mean | 21.87 ms |
| p50 | 14 ms |
| p90 | 17 ms |
| p95 | 19 ms |
| p99 | 240 ms |
| p99.9 | 245 ms |
| max | 255 ms |
| GC rate | 2.13 /sec |
| Our CPU share | 5.2 % |

**CPU package distribution:**

| Package | % |
|---|---|
| springframework | 36.7 |
| micrometer | 16.7 |
| catalina | 14.9 |
| jackson | 12.5 |
| opentelemetry | 8.5 |
| callibrity.mocapi | 4.6 |
| logback | 1.6 |
| methodical | 1.3 |
| ripcurl | 0.4 |

**Top 5 methods in our code:**

| Samples | Method |
|---|---|
| 31 | `McpObservationInterceptor.intercept` |
| 20 | `InputSchemaValidatingInterceptor.intercept` |
| 12 | `McpToolsService.toCallToolResult` |
| 11 | `StreamableHttpController.handleCall` |
| 8 | `StreamableHttpTransport.commit` |

**Notes:**

- This is the first clean post-migration baseline. Ripcurl 2.8 is
  performance-neutral vs 2.7 at this workload.
- Two earlier soaks in the same session produced suspicious numbers
  due to a buggy `Validator` cache I introduced and reverted
  (thread-safety issue in json-sKema 0.29 under concurrent load).
  Ignore those.
- Earlier "0.55 GCs/sec" number on 2026-04-19 pre-migration was an
  artifact of that same bug; steady-state GC rate for this stack is
  ~2/sec.
- Jaeger showed clean parent-child spans (`http post /mcp` → `mcp.tool`),
  confirming context propagation across the HTTP → VT boundary is
  working.
