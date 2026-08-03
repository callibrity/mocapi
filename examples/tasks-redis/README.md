# Mocapi Example — Tasks on a Redis-backed TaskStore (kill-and-resume)

The [tasks example](../tasks/README.md), re-based onto a **real Redis `TaskStore`**:

- Spring Boot's Docker Compose support starts the Redis in [`compose.yaml`](compose.yaml)
  when the app boots and contributes its connection details.
- `substrate-redis` builds Substrate's Atom SPI from the resulting
  `RedisConnectionFactory`; `codec-jackson` supplies the codec.
- `mocapi-tasks-substrate`'s autoconfiguration then swaps the in-memory `TaskStore`
  for the Substrate-backed one — watch for
  `Using the Substrate-backed TaskStore (key prefix 'mocapi:tasks:')` at startup.

Because `spring.docker.compose.lifecycle-management=start-only`, the Redis (and the
task state in it) **outlives application restarts**. That enables the demo the
in-memory store cannot do:

## Kill-and-resume walkthrough

Requests live in [`mcp-example-requests.http`](mcp-example-requests.http) (port 8083).

1. Run the app: `mvn -pl examples/tasks-redis -am spring-boot:run`
   (Docker must be running; the app starts Redis itself.)
2. Call `confirmed_report` as a task (request 3) and poll `tasks/get` (request 4)
   until `input_required` — the task is now parked in Redis with a pending
   elicitation, no thread waiting anywhere.
3. **Kill the app** (Ctrl-C). Redis stays up.
4. Restart the app (same command).
5. Answer the elicitation via `tasks/update` (request 5) — MRTR replay re-runs the
   handler against the ledger in Redis, the elicit call returns the answer this
   time, and `tasks/get` (request 6) shows `completed` with the published report.

There was no in-flight thread to preserve: resume is a pure replay through the
store, which is why a restart in the middle is harmless.

Also worth trying: kill mid-`batch_resize` instead. The record survives in Redis
(still `working`, `statusMessage` frozen at the last emitted item) but execution was
process-local, so nothing re-runs it — it parks until its TTL (10 minutes) reaps it.
That contrast — elicitation tasks resume, plain in-flight tasks do not — is the
honest shape of the extension's durability story.

When done: `docker compose -f examples/tasks-redis/compose.yaml down`
