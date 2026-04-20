# Mocapi Performance Benchmarking

Periodic soak-testing + CPU/GC profiling for mocapi running against the
in-memory example. Run this workflow when you want to detect
regressions, validate a performance-focused change, or publish
steady-state numbers.

**Audience:** you, later. Or anyone doing a 2.x → 3.x migration and
wanting to confirm nothing got worse.

## When to run

- Before cutting a mocapi release.
- After any of these changes: Methodical / ripcurl / Micrometer /
  Spring Boot / OpenTelemetry version bump; interceptor chain
  rework; a parameter-resolver change; a new hot-path feature.
- If someone files a "mocapi feels slow" issue. Run this and the
  numbers either back them up or rule it out in 10 minutes.
- Once a quarter anyway, as rolling baseline.

## Prerequisites

One-time setup (minute, once, stays installed):

```bash
# Jaeger all-in-one in Docker, listens on OTLP/HTTP (4318) and UI (16686)
docker run -d --rm --name jaeger \
    -p 16686:16686 \
    -p 4318:4318 \
    jaegertracing/all-in-one:latest
```

### Temporary benchmarking deps

The example modules ship minimal by default — no observability stack,
no actuator, no tracing — because demo apps shouldn't pay for what
they don't need. Add these to
`examples/in-memory/pom.xml` **for the duration of the benchmarking
run only**, then back them out when you're done:

```xml
<!-- observation interceptors + customizers -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-o11y</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- metrics + actuator infrastructure -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-actuator</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- tracing bridge + Spring Boot 4 OTel SDK autoconfig (3 artifacts) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-micrometer-tracing-opentelemetry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>

<!-- OTLP exporter to ship spans to Jaeger -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

And add these properties to
`examples/in-memory/src/main/resources/application.properties`
for the duration of the run:

```properties
management.endpoints.web.exposure.include=*
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
management.tracing.sampling.probability=1.0

# Disable OTLP metrics export — Jaeger only accepts traces, and without
# this the OtlpMeterRegistry logs 404s every minute. Metrics stay
# readable via /actuator/metrics.
management.otlp.metrics.export.enabled=false
```

Revert both the pom addition and the properties block after the
benchmarking session ends — the examples are intentionally lean, and
the base `application.properties` should stay operational without
any of this wired in.

## Workflow

Assume the in-memory example is running on `localhost:8080` via
whatever you use (IntelliJ, `mvn spring-boot:run`, a built jar).

### 1. Initialize a session

```bash
curl -s -m 5 -X POST \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json,text/event-stream' \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","clientInfo":{"name":"soak","version":"1"},"capabilities":{}}}' \
    localhost:8080/mcp \
    -D /tmp/ih.txt > /dev/null
SID=$(grep -i 'MCP-Session-Id:' /tmp/ih.txt | tr -d '\r\n' | awk '{print $2}')
echo "$SID" > /tmp/sid.txt

curl -s -m 3 -X POST \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json,text/event-stream' \
    -H "MCP-Session-Id: $SID" \
    -H 'MCP-Protocol-Version: 2025-11-25' \
    -d '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
    localhost:8080/mcp > /dev/null
echo "session: $SID"
```

### 2. JIT warmup

Drive the app for ~45 seconds to let HotSpot JIT the hot path.
Profile-time measurements on unwarmed code lie.

```bash
rm -f /tmp/soak-*.log /tmp/soak-count-*.txt
for i in $(seq 1 8); do docs/perf/soak.sh 45 1 $i & done
wait
echo "warmup: $(cat /tmp/soak-count-*.txt | awk '{s+=$1} END {print s}') calls"
```

Expect ~18-20k calls. If dramatically lower, the server isn't
responding at rate — investigate before starting the measured run.

### 3. Start JFR profiling

```bash
PID=$(lsof -tiTCP:8080 -sTCP:LISTEN | head -1)
jcmd $PID JFR.start \
    duration=210s \
    filename=/tmp/mocapi-soak.jfr \
    name=mocapi \
    settings=profile
```

The `profile` settings preset captures CPU samples, allocations,
GC, locks, and safepoints. 210s duration covers the upcoming
180s soak with ~30s buffer.

### 4. Measured soak — 3 minutes, 16-way parallel

```bash
rm -f /tmp/soak-*.log /tmp/soak-count-*.txt
date "+start %H:%M:%S"
for i in $(seq 1 16); do docs/perf/soak.sh 180 1 $i & done
wait
date "+end %H:%M:%S"

echo "calls: $(cat /tmp/soak-count-*.txt | awk '{s+=$1} END {print s}')"
cat /tmp/soak-*.log | awk '{print $2}' | sort | uniq -c  # HTTP codes
cat /tmp/soak-*.log | awk '{print $3}' | sort -n > /tmp/lat.txt
N=$(wc -l < /tmp/lat.txt)
echo "mean: $(awk '{s+=$1;n++} END{printf "%.2f",s/n}' /tmp/lat.txt) ms"
awk -v n=$N 'BEGIN{split("0.50 0.90 0.95 0.99 0.999",p," ");
  for(i in p){idx=int(n*p[i]); if(idx<1)idx=1; name[idx]=p[i]}}
  NR in name {printf "p%s: %s ms\n",name[NR],$1}' /tmp/lat.txt
echo "max: $(tail -1 /tmp/lat.txt) ms"
```

### 5. Dump & analyze JFR

Wait for the recording to close (210s after `JFR.start`), then
force-dump and analyze:

```bash
jcmd $PID JFR.dump name=mocapi filename=/tmp/mocapi-soak.jfr
jfr print --events jdk.ExecutionSample /tmp/mocapi-soak.jfr > /tmp/samples.txt

python3 - <<'PY'
from collections import Counter
with open('/tmp/samples.txt') as f: text = f.read()
blocks = text.split('jdk.ExecutionSample {')
ours = ('com.callibrity.', 'org.jwcarman.')
pkg = Counter(); leaf = Counter()
for b in blocks[1:]:
    in_stack = False; frames = []
    for line in b.split('\n'):
        if 'stackTrace' in line: in_stack = True; continue
        if not in_stack: continue
        if line.strip().startswith(']'): break
        t = line.strip()
        if t: frames.append(t)
    seen = set()
    for f in frames[:30]:
        for p in ['callibrity.mocapi','micrometer','opentelemetry',
                  'springframework','logback','jackson','catalina',
                  'methodical','ripcurl']:
            if p in f and p not in seen:
                pkg[p] += 1; seen.add(p); break
    for f in frames:
        if any(f.startswith(p) for p in ours):
            leaf[f.split(' line:')[0].split('(')[0]] += 1
            break
total = len(blocks) - 1
print(f"samples: {total}, our code top-most: {sum(leaf.values())} "
      f"({100*sum(leaf.values())/total:.1f}%)")
for p,n in pkg.most_common():
    print(f"  {n:5d} ({100*n/total:4.1f}%)  {p}")
print("\ntop 10 our-code:")
for m,n in leaf.most_common(10): print(f"  {n:4d}  {m}")
PY
```

GC rate:

```bash
echo "GCs: $(jfr print --events jdk.GarbageCollection /tmp/mocapi-soak.jfr | grep -c 'jdk.GarbageCollection {')"
```

### 6. Spot-check slow traces in Jaeger

Open `http://localhost:16686`, filter service `mocapi-example-in-memory`,
set "Min Duration" to 100ms, limit 50. Any traces beyond your
expected p99 are candidates for investigation. Drill into the
waterfall — the child `mcp.tool` span vs parent `http post /mcp`
span should be co-located with a tiny gap; if the gap is large,
investigation belongs in the transport layer.

## Baseline numbers

Capture your numbers in `docs/perf/history.md` when you run. The
first entry is below as a reference point.

### 2026-04-19 — baseline

| | Value |
|---|---|
| Stack | Methodical 0.7.0 / ripcurl 2.8.0-SNAPSHOT / Spring Boot 4.0.5 / JDK 25 (Liberica) |
| Hardware | ARM64 macOS, 700MB heap, dev laptop |
| Load | 16-way parallel sync loop, 180s |
| Total calls | 101,621 |
| Errors | 0 |
| Throughput | 564 req/s |
| p50 | 14 ms |
| p90 | 17 ms |
| p95 | 19 ms |
| p99 | 240 ms |
| p99.9 | 245 ms |
| max | 255 ms |
| mean | 21.87 ms |
| GC rate | 2.13 /sec |
| Our code CPU share | 5.2 % |
| Top CPU in our code | `McpObservationInterceptor.intercept` (31), `InputSchemaValidatingInterceptor.intercept` (20), `toCallToolResult` (12) |

### CPU package distribution (what's normal)

| Package | % CPU | What |
|---|---|---|
| springframework | 35-40 % | Spring MVC dispatch, converter negotiation, handler mapping |
| micrometer | 14-17 % | Observation lifecycle, tag writes, handler fan-out |
| catalina | 12-15 % | Tomcat request accept, header parse, worker dispatch |
| jackson | 10-14 % | Request body parse + response serialize (tree-model) |
| opentelemetry | 7-12 % | Span creation, ThreadLocal context storage, propagators |
| logback | 1-8 % | Mostly TurboFilterList evaluation on `log.trace()` |
| callibrity.mocapi | 4-6 % | Our interceptor chain + service dispatch |
| methodical | 1-2 % | Reflective invocation + Cursor chain walk |
| ripcurl | 0.5-1 % | JSON-RPC dispatch + resolver chain |

Large deviations from these bands indicate a regression or a
changed workload shape.

## Interpretation guide

- **Errors > 0** — always an immediate investigation. At 100k+
  calls, any non-200 deserves a look.
- **p50 moving > 2ms** — real signal. Investigate.
- **p99 moving > 50ms** — the long tail is dominated by JVM
  safepoints / GC / OTel export hiccups. Look for clustering in
  Jaeger first; if all slow traces fall in one second, it's a
  pause event, not steady-state.
- **GC rate > 3/sec** — allocation pressure regression. Run with
  `-XX:+UnlockDiagnosticVMOptions -XX:+FlightRecorder` and the
  `allocation` JFR preset to find the source.
- **`mcp.tool.active` LongTaskTimer not zero post-soak** — hung
  invocations. Check for long-lived tools (elicitation, streaming),
  and verify with `/actuator/threaddump`.
- **Our CPU share > 8 %** — we've introduced a hot spot. Compare
  top methods against baseline.

## Known pitfalls

- **`InputSchemaValidatingInterceptor` Validator must be fresh
  per call.** json-sKema 0.29+'s `Validator` is not thread-safe —
  the internal `SchemaVisitor` uses a shared `ArrayList` that
  races under concurrent load. Do NOT cache
  `Validator.forSchema(schema)` in the interceptor field; allocate
  per call. (Violated during 2026-04-19 benchmarking; produced
  sporadic `ArrayIndexOutOfBoundsException`s under 16-way load.)
- **`hello-elicitation` tool blocks forever from non-elicitation
  clients.** It's by design — the tool waits for the client to
  fill out a form. A non-interactive soak that calls it will hang
  that invocation and spike `mcp.tool.active`. The included
  `soak.sh` hits only `hello` and `rot-13-tool.encode` for this
  reason.
- **OTLP metrics exporter 404s against Jaeger.** Jaeger's
  all-in-one accepts traces at `/v1/traces` but not metrics at
  `/v1/metrics`. Set
  `management.otlp.metrics.export.enabled=false` in dev properties.
- **Stale `~/.m2` pom resolution** after changing a mocapi
  version. If you bump a dep version in the reactor,
  `mvn install -DskipTests` on the affected modules before running
  the example — otherwise the example picks up the previously
  installed pom.
- **JIT-cold measurements lie.** Always warm up before the
  measured run. A 45-second 8-way warmup at our scale puts all
  hot methods into C2-compiled state.
- **Sampling probability affects throughput.** The baseline above
  is at 100 % trace sampling, which maximizes the observability
  overhead. Production deployments typically sample 0.05 - 0.10;
  their throughput will be ~5-10 % higher than the baseline, not
  a regression.

## Post-soak cleanup

- JFR files live in `/tmp`; archive interesting ones under
  `docs/perf/jfr/<date>-<label>.jfr` before they get blown away.
- `/tmp/soak-*.log` and `/tmp/soak-count-*.txt` are scratch — no
  cleanup needed, they'll be overwritten on next run.
- Jaeger's in-memory span buffer has finite capacity; after ~10k
  traces the oldest drop off. Restart the container if you want
  a fresh view.

## History

See [`history.md`](./history.md) for run-over-run numbers.
