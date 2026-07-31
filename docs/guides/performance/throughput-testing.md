# Throughput & saturation testing

This is the runbook for finding a mocapi server's **request-throughput ceiling** —
how many full MCP round trips per second it sustains, and how it behaves past
saturation. It is distinct from [benchmarking.md](benchmarking.md), which is a
JFR-profiling / soak runbook for tracking regressions in the *shape* of the work.

The load driver is [`throughput.js`](throughput.js) (k6). It drives a real
`tools/call` — envelope parse, routing-header validation, dispatch, argument
schema-validation, handler, structured-result + serverInfo `_meta` serialization —
not a trivial ping.

## The one rule that makes the number trustworthy

**Run the load generator on a different machine than the server.** A load
generator at high request rates burns nearly as much CPU as the server it drives.
Co-located, you are measuring *server + generator competing for the same cores*,
and the server sees only a fraction of the machine — the number understates the
server, sometimes by ~2×. Two boxes on a wired network is the whole methodology;
everything else is detail.

## Setup

### Server (the machine under test)

```bash
# OS: the two limits that cap you first at 100k+ req/s
ulimit -n 1048576
sudo sysctl -w net.core.somaxconn=65535 net.ipv4.tcp_max_syn_backlog=65535

# Fixed heap (no resize pauses) + opened Tomcat connector limits so the SERVER,
# not an arbitrary connector cap, is the ceiling. Quiet logging so I/O isn't the bottleneck.
java -Xms8g -Xmx8g -jar mocapi-example-http-*.jar \
  --server.tomcat.max-connections=200000 \
  --server.tomcat.accept-count=20000 \
  --logging.level.root=WARN
```

Build the example server with `mvn -pl examples/http -am package -DskipTests`
(jar in `examples/http/target/`), or point `throughput.js` at your own mocapi app.

### Generator (a separate machine)

```bash
ulimit -n 1048576                                   # client needs FDs too
# If you see connection failures at very high rates, widen the ephemeral port range
# and allow TIME_WAIT reuse (Linux):
sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535" net.ipv4.tcp_tw_reuse=1
```

## Run the sweep

Throughput rises with concurrency until the bottleneck, then flattens while latency
climbs — the plateau is the ceiling. Sweep VUs and record req/s, p95, and error
rate at each level:

```bash
for VUS in 100 250 500 1000 2000 4000; do
  echo "== $VUS VUs =="
  TARGET=<server-ip>:8080 VUS=$VUS DURATION=30s k6 run --quiet throughput.js \
    | grep -E "http_reqs|http_req_duration|http_req_failed"
done
```

## Reading the result honestly

A throughput number is only meaningful with two things in view:

1. **Server CPU at peak.** Watch `mpstat -P ALL 1` (or `htop`) on the server. If it
   pegs every core while the generator sits comfortably, you found the *server's*
   ceiling. If the generator is also maxed, the *client* is your limit — add a
   second generator or a beefier client box before trusting the number.
2. **The network link.** ~120k responses/s at ~600 bytes each is ~600 Mbit/s+. On
   anything less than wired gigabit, the *link* saturates first and you will blame
   the server unfairly. Confirm you are not bottlenecked on bandwidth.

The **shape** of the curve matters more than the peak:

- **Throughput plateaus, latency rises linearly, errors stay at 0%** → healthy
  saturation. The server is at capacity and degrading gracefully; past the knee,
  concurrency buys only queuing delay. This is the good outcome.
- **Errors climb, or throughput collapses toward zero** → the server (or a limit
  around it — FDs, accept backlog, heap) is failing, not just saturating.
  Investigate what broke, not just when.

## Reference: single-box run (understates the server, on purpose)

For calibration — a run with the generator **co-located** on the same 16-core
laptop (Apple M4 Max), which is the *wrong* way to do it and therefore a floor,
not a ceiling. `hello` tool, full round trip, 0% errors throughout:

| VUs  | req/s | p95    | errors |
|------|-------|--------|--------|
| 100  | 110k  | 1.6ms  | 0%     |
| 250  | 119k  | 3.5ms  | 0%     |
| 500  | 113k  | 6.6ms  | 0%     |
| 1000 | 106k  | 12.4ms | 0%     |
| 2000 | 97k   | 22ms   | 0%     |
| 4000 | 69k   | 55ms   | 0%     |
| 8000 | 30k   | 293ms  | 0%     |

CPU sampling during the run showed the server at ~7 cores and k6 at ~6.5 — the
generator was eating nearly half the machine, which is exactly why this is a floor.
The peak plateau (~120k) and the **zero errors out to 8,000 concurrent connections**
(throughput trades down to latency past saturation, but nothing fails) are the
per-request virtual-thread model working: 8,000 in-flight requests are 8,000 cheap
virtual threads on ~16 carriers, not 8,000 contended platform threads (ADR-0006).
Run it two-box and the server, given all its cores, clears this comfortably.
