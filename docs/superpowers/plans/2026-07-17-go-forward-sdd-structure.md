# Go-Forward SDD Structure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the repo conform to the superpowers SDD pipeline and close the roadmap / non-negotiables / PRD gaps, without disturbing the existing ADR + design-doc discipline.

**Architecture:** Documentation + process only. New docs: `docs/constitution.md`, `docs/roadmap.md`, `docs/superpowers/README.md`. Edited docs: `CLAUDE.md` (project root — invariants pointer + subsystem read-map), `PRD.md` (refresh), `CONTRIBUTING.md` (pipeline pointer), `docs/README.md` (index), `docs/plans/2026-06-11-mcp-2026-07-28-migration.md` (archive note). Source of truth for all content: `docs/superpowers/specs/2026-07-17-go-forward-sdd-structure-design.md` — read it first.

**Tech Stack:** Markdown. One CLAUDE.md edit (no build impact).

## Global Constraints

- **Do not commit.** The maintainer commits (repo rule: "commit only when asked"). Each task ends by *staging* (`git add`) and leaving the change for review. If later asked to commit, a docs-only or CLAUDE.md-only commit MUST include `[skip ci]`.
- **No file churn / no history rewrites.** Do not move existing files. Do not rewrite historical ADRs or the migration plan/diff (out of scope).
- **Read the spec first:** `docs/superpowers/specs/2026-07-17-go-forward-sdd-structure-design.md`. Where this plan says "match the spec," reproduce that section's substance faithfully.
- **Link discipline:** every ADR / design-doc / plan path referenced in a new doc MUST resolve to an existing file. Verify before staging.
- Execution: subagent-driven; each task is independently reviewable.

---

### Task 1: Constitution — `docs/constitution.md`

**Files:**
- Create: `docs/constitution.md`

**Interfaces:**
- Produces: the citable invariant anchors `I1`–`I9` referenced by CLAUDE.md (Task 2) and PRD.md (Task 4).

- [ ] **Step 1: Confirm every ADR to be cited exists**

Run:
```bash
cd /Users/jcarman/IdeaProjects/mocapi
for n in 0001 0002 0010 0011 0012 0013 0014 0019 0020 0022 0023; do
  ls docs/adr/${n}-*.md >/dev/null 2>&1 && echo "ok $n" || echo "MISSING $n"; done
```
Expected: all `ok`. If any `MISSING`, stop and report — do not invent a citation.

- [ ] **Step 2: Write `docs/constitution.md`**

Header + intro:
```markdown
# mocapi Constitution — Architectural Invariants

These are the load-bearing invariants of mocapi: the properties a change must
not violate **without a superseding ADR**. This file is an *index of
invariants*, not a restatement of the ADRs — each entry links its governing
decision. Conduct rules (formatting, warnings, imports, workflow) live in
`CLAUDE.md`; product direction lives in `docs/roadmap.md`.

Changing an invariant means writing a new ADR (status Accepted) that supersedes
the cited one, updating the affected `docs/design/*.md` in the same change, and
updating the entry here.
```

Then the numbered invariants (one or two sentences each; link the ADR files that Step 1 confirmed). Reproduce the I1–I9 set from spec §3 verbatim in substance:

- **I1 — Stateless request model** → ADR-0019, ADR-0020
- **I2 — Single protocol/transport coupling** (`McpServer`↔`McpTransport`; core has no I/O-framework dependency) → ADR-0002
- **I3 — Spec-compliance target** (current MCP revision 2026-07-28; clean-break, no deprecated-at-introduction features) → ADR-0019, ADR-0022
- **I4 — Module boundaries & packaging** → ADR-0001
- **I5 — Static handler discovery** → ADR-0010
- **I6 — Declared not-implemented surface** → ADR-0022
- **I7 — Model is 1:1 with the MCP schema** → ADR-0014
- **I8 — Authorization model** (bearer + mandatory audience; RFC 9728; Guard SPI) → ADR-0013, ADR-0012
- **I9 — Error-code allocation** (mocapi-private codes in −32000..−32019; spec codes verbatim) → ADR-0023

Use relative links, e.g. `[ADR-0020](adr/0020-stateless-request-model.md)` (resolve exact filenames from `docs/adr/`).

- [ ] **Step 3: Verify links resolve**

Run:
```bash
grep -oE 'adr/[0-9]{4}-[a-z0-9-]+\.md' docs/constitution.md | sort -u | while read p; do
  ls "docs/$p" >/dev/null 2>&1 && echo "ok $p" || echo "BROKEN $p"; done
```
Expected: all `ok`.

- [ ] **Step 4: Stage (do not commit)**

```bash
git add docs/constitution.md
```

---

### Task 2: CLAUDE.md — invariants pointer + subsystem read-map

**Files:**
- Modify: `CLAUDE.md` (project root, `/Users/jcarman/IdeaProjects/mocapi/CLAUDE.md`)

**Interfaces:**
- Consumes: `docs/constitution.md` (Task 1) and existing `docs/design/*.md` + `docs/adr/*.md`.

- [ ] **Step 1: Add an "Architectural invariants" pointer**

In the project `CLAUDE.md`, add a short subsection (near the code-quality/workflow rules):
```markdown
## Architectural invariants

The load-bearing invariants live in [`docs/constitution.md`](docs/constitution.md).
Do not violate one without a superseding ADR (see the ADR rule above).
```

- [ ] **Step 2: Add a subsystem read-on-demand map**

Extend the existing read-on-demand pattern to subsystems. Add:
```markdown
## Read-before-you-touch (subsystems)

Load the matching docs at the start of work in that area:

- Transports / `McpServer`↔`McpTransport` → `docs/design/transports.md`, ADR-0002/0003/0004
- Authorization (OAuth2, guards) → `docs/design/authorization-model.md`, ADR-0012/0013
- Model / wire types → ADR-0014, `docs/plans/2026-07-28-schema.ts`
- Handlers & discovery → `docs/design/handlers.md`, ADR-0010/0011
- Elicitation / MRTR → `docs/design/elicitation-mrtr.md`, ADR-0021/0024/0025
- Observability → `docs/design/observability-stack.md`, ADR-0017

Every subagent dispatch should name the specific docs/ADRs/invariants the task must honor.
```
Verify each referenced path exists before finalizing (adjust ADR numbers to real filenames).

- [ ] **Step 3: Verify referenced paths exist**

Run:
```bash
for f in docs/constitution.md docs/design/transports.md docs/design/authorization-model.md \
         docs/design/handlers.md docs/design/elicitation-mrtr.md docs/design/observability-stack.md \
         docs/plans/2026-07-28-schema.ts; do
  ls "$f" >/dev/null 2>&1 && echo "ok $f" || echo "MISSING $f"; done
```
Expected: all `ok`.

- [ ] **Step 4: Stage (do not commit)**

```bash
git add CLAUDE.md
```

---

### Task 3: Roadmap — `docs/roadmap.md`

**Files:**
- Create: `docs/roadmap.md`

- [ ] **Step 1: Write `docs/roadmap.md`** (structure = Now / Next / Later; match spec §4)

```markdown
# mocapi Roadmap

Forward direction only — decisions already made live in `docs/adr/`, invariants
in `docs/constitution.md`. Cadence is 0.x: minor = headline features and
possible pre-1.0 breaks; patch = hotfix.

## Now
- Finalize MCP 2026-07-28 compliance: re-diff against the final released schema
  and re-pin the snapshot at spec release (migration plan Task 9.3).
- Land the transport inbound-consistency cleanup
  ([plan](superpowers/plans/2026-07-17-transport-inbound-consistency.md)).
- Land the ADR-0002 contract truth-up
  ([plan](superpowers/plans/2026-07-17-adr-0002-contract-truthup.md)).

## Next
- Jakarta Validation via Methodical once it ships: annotate model records, map
  violations to JSON-RPC −32602.
- Demo code + runner script for the stateless 2026-07-28 example server.
- Resolve the deferred `HeaderMismatch`-into-`mocapi-model` ADR question.

## Later (TBD — maintainer to fill)
- Path to 1.0.
- Additional transports.
- Extension-track stance (Tasks / MCP Apps) revisits.
```

- [ ] **Step 2: Verify the two plan links resolve**

Run:
```bash
ls docs/superpowers/plans/2026-07-17-transport-inbound-consistency.md \
   docs/superpowers/plans/2026-07-17-adr-0002-contract-truthup.md
```
Expected: both listed.

- [ ] **Step 3: Stage (do not commit)**

```bash
git add docs/roadmap.md
```

---

### Task 4: PRD refresh — `PRD.md`

**Files:**
- Modify: `PRD.md`

- [ ] **Step 1: Find the stale content**

Run:
```bash
grep -nE "2025-11-25|mocapi-example([^s]|$)|Java 25|Spring Boot 4\.0\.5|3\.5\.3" PRD.md
```
Note each hit; these are the lines to correct.

- [ ] **Step 2: Apply the refresh**

- Change the spec target from `2025-11-25` to **`2026-07-28`**.
- Correct the "How to run" module: the runnable apps are `mocapi-examples` and `mocapi-conformance` (there is no `mocapi-example`). Verify current names: `ls -d mocapi-example* mocapi-conformance`.
- Verify the stack line (Java / Spring Boot / Maven versions) against the root `pom.xml` `<properties>` and correct any drift: `grep -E "java.version|spring-boot" pom.xml | head`.
- Add, near the top, two pointers so volatile detail is delegated:
  `Direction: see [docs/roadmap.md](docs/roadmap.md). Invariants: see [docs/constitution.md](docs/constitution.md).`
- Keep PRD to a one-page "what & why"; do not duplicate roadmap/constitution content.

- [ ] **Step 3: Verify no stale markers remain**

Run:
```bash
grep -nE "2025-11-25|mocapi-example([^s]|$)" PRD.md || echo "CLEAN"
```
Expected: `CLEAN`.

- [ ] **Step 4: Stage (do not commit)**

```bash
git add PRD.md
```

---

### Task 5: Pipeline README + CONTRIBUTING pointer

**Files:**
- Create: `docs/superpowers/README.md`
- Modify: `CONTRIBUTING.md`

- [ ] **Step 1: Write `docs/superpowers/README.md`** (match spec §1 + §6 dispatch norm)

```markdown
# SDD Workflow (superpowers)

mocapi uses the superpowers spec-driven-development pipeline. This file states
*when* it applies and *where* artifacts live; the skills themselves define the
*how*.

## The flow
Non-trivial change → `brainstorming` (spec → `docs/superpowers/specs/`) →
`writing-plans` (plan → `docs/superpowers/plans/`) →
`subagent-driven-development` (default) with TDD, `verification-before-completion`,
and `requesting-code-review` gates.

Architecturally significant changes (see the trigger list in `CLAUDE.md`) also
produce an ADR + design-doc update; the spec references the ADR.

## When it applies
- **Non-trivial** (new feature, new/changed SPI, transport/contract change, or
  anything on CLAUDE.md's "architecturally significant" list): full pipeline.
- **Trivial** (typo, dependency bump, doc-only fix, mechanical refactor with no
  contract change): direct edit — no spec/plan.

## Execution mode
Subagent-driven by default; inline is the exception, used where a task is small,
tightly-coupled, or fast-iteration.

## Context hygiene
Every subagent dispatch names the specific design docs / ADRs / constitution
invariants the task must honor, so a fresh-context subagent loads the right
material. See CLAUDE.md's "Read-before-you-touch" map.
```

- [ ] **Step 2: Add a pointer from `CONTRIBUTING.md`**

Add a short section to `CONTRIBUTING.md` linking `docs/superpowers/README.md` as the workflow for non-trivial changes. Match the file's existing heading style (inspect it first).

- [ ] **Step 3: Verify links**

Run: `ls docs/superpowers/README.md && grep -n "superpowers/README.md" CONTRIBUTING.md`
Expected: file listed and the pointer present.

- [ ] **Step 4: Stage (do not commit)**

```bash
git add docs/superpowers/README.md CONTRIBUTING.md
```

---

### Task 6: Legacy archive note + docs index

**Files:**
- Modify: `docs/plans/2026-06-11-mcp-2026-07-28-migration.md` (one-line note only)
- Modify: `docs/README.md` (index the two new docs)

- [ ] **Step 1: Add the archive note**

At the very top of `docs/plans/2026-06-11-mcp-2026-07-28-migration.md`, add a single blockquote line:
```markdown
> **Archived (pre-superpowers migration artifact).** New work follows the
> pipeline in [`../superpowers/README.md`](../superpowers/README.md). The MCP
> schema snapshots in this directory remain live reference material.
```
Do not alter the rest of the file.

- [ ] **Step 2: Index the new docs in `docs/README.md`**

Inspect `docs/README.md` first, then add entries pointing to `constitution.md`, `roadmap.md`, and `superpowers/README.md`, matching the file's existing list style.

- [ ] **Step 3: Full link sweep across the new/edited docs**

Run:
```bash
grep -rhoE '\]\(([a-zA-Z0-9._/-]+\.md)\)' docs/constitution.md docs/roadmap.md \
  docs/superpowers/README.md docs/README.md | sed -E 's/.*\(([^)]+)\)/\1/' | sort -u
```
Manually confirm each relative target exists from its containing directory (resolve `../` correctly).

- [ ] **Step 4: Stage (do not commit)**

```bash
git add docs/plans/2026-06-11-mcp-2026-07-28-migration.md docs/README.md
```

---

## Self-Review

- **Spec coverage:** §1 pipeline → Task 5; §2 doc map + archive → Tasks 5/6; §3 constitution + CLAUDE.md pointer → Tasks 1/2; §4 roadmap → Task 3; §5 PRD → Task 4; §6 context hygiene → Task 2 (read-map) + Task 5 (dispatch norm). All six spec sections covered.
- **Placeholders:** the only "TBD"s are the roadmap "Later" lane, which the spec explicitly designates as maintainer-owned — intentional, not a plan gap.
- **Consistency:** invariant anchors `I1`–`I9` defined in Task 1 are what Task 2/Task 4 point to; new-doc paths used in later tasks match the create paths in earlier tasks.
- **Deferred commits:** no task commits; each stages and leaves review to the maintainer, per repo rule.
