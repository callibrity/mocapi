# Mocapi Performance History

Append a row here after each soak. See
[`benchmarking.md`](./benchmarking.md) for the methodology. Keep
entries chronological (newest at top).

**Row format:** date · git SHA · stack summary · key numbers · notes.
Record the **exact JDK build** (`java -version` + `IMPLEMENTOR`) and the
**GC** in use — patch level and collector are real variables and the
2026-04-19 baseline failed to capture them (see that entry's notes).

---

## 2026-07-28 — stateless MCP 2026-07-28 rewrite (first post-clean-break soak)

- **SHA:** `1d628eb4` (main, PR #8 merged — stateless 2026-07-28 clean break)
- **Stack:** Methodical 0.9.2 / ripcurl 2.11.0 / Spring Boot 4.0.5 (Micrometer + OpenTelemetry via the Spring Boot 4.0.5 BOM, same as baseline)
- **JDK:** Liberica (BellSoft) **25.0.3+11-LTS**, HotSpot 64-Bit Server VM, **default GC (unpinned)**
- **Hardware:** ARM64 macOS, 700 MB heap (`-Xms700m -Xmx700m`), dev laptop
- **Config:** full observability, 100 % trace sampling, OTLP to Jaeger all-in-one, OTLP metrics export disabled (temp benchmarking deps on `examples/http`, reverted after run)
- **Load:** 16-way parallel synchronous loop, 180 s, `hello` + `rot-13-tool.encode` mix, stateless header set (`MCP-Protocol-Version` / `Mcp-Method` / `Mcp-Name`, no session)
- **Results (Δ vs 2026-04-19 baseline):**

| Metric | Value | Baseline | Δ |
|---|---|---|---|
| Calls | 101,400 | 101,621 | — |
| Errors | 0 | 0 | ✅ |
| Throughput | 563.3 req/s | 564 | flat |
| mean | 21.24 ms | 21.87 | −0.6 |
| p50 | 14 ms | 14 | 0 |
| p90 | 17 ms | 17 | 0 |
| p95 | 19 ms | 19 | 0 |
| p99 | 245 ms | 240 | +5 |
| p99.9 | 253 ms | 245 | +8 |
| max | 263 ms | 255 | +8 |
| GC rate | ~0.25 /sec (45 GCs) | 2.13 /sec | see notes |
| Our CPU share | 7.3 % | 5.2 % | +2.1 pp |

**CPU package distribution:**

| Package | % |
|---|---|
| springframework | 38.4 |
| micrometer | 17.2 |
| jackson | 16.3 |
| catalina | 14.6 |
| opentelemetry | 4.5 |
| callibrity.mocapi | 4.2 |
| methodical | 3.6 |
| logback | 2.5 |
| ripcurl | 1.5 |

**Top 5 methods in our code** (1,608 execution samples):

| Samples | Method |
|---|---|
| 12 | `methodical.jakarta.Annotations.lookup` |
| 10 | `McpHandlerObservationInterceptor.intercept` |
| 10 | `methodical.DefaultMethodInvoker$Cursor.proceed` |
| 9 | `methodical.DefaultMethodInvoker.invokeMethod` |
| 8 | `McpToolsService.invokeTool` |

**Notes:**

- **No regression.** Throughput and the p50/p90/p95 tail are flat vs the
  session-based baseline; p99 is +5 ms (runbook investigate line is +50 ms).
  The stateless clean break is performance-neutral at this workload.
- **JDK caveat.** This run is Liberica **25.0.3** (released 2026-04-21). The
  2026-04-19 baseline predates that build and recorded only "JDK 25 (Liberica)",
  so its exact patch is unknown — same distribution/major/arch/VM, unknown patch
  drift. Immaterial to the throughput/latency verdict (patch releases are
  bug/security, not perf reworks), but a real data-quality gap now closed for
  future runs.
- **GC rate not comparable.** Neither run pinned a collector. At a 700 MB heap
  (below G1's ~1792 MB ergonomic threshold) HotSpot may select SerialGC, so the
  2.13 → 0.25 /sec drop likely reflects a different ergonomic GC choice and/or
  the JDK patch drift, not a real allocation change. Pin `-XX:+UseG1GC` (or
  record the actual collector) to make this figure meaningful next time.
- **Our CPU share 5.2 % → 7.3 %.** Under the 8 % action threshold. Expected: the
  observation design moved to a single JSON-RPC-layer interceptor
  (`McpHandlerObservationInterceptor`), shifting the JFR mix; on 1,608 samples a
  ~2 pp move is within noise. New top frame `methodical.jakarta.Annotations.lookup`
  is a reflective annotation lookup on the hot path — candidate micro-opt (cache it).
- **Harness ported.** The committed `soak.sh` + `benchmarking.md` still assume the
  old session handshake and will not drive a 2026-07-28 server as-is; this run used
  a stateless-ported copy. Porting them in-tree is a pending follow-up.

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
