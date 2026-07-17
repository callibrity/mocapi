# Go-Forward SDD Structure for mocapi — Design

**Date:** 2026-07-17
**Status:** Approved (brainstorming), pending spec review
**Topic:** Documentation + process structure that makes the repo conform to the
superpowers SDD pipeline and closes the roadmap / non-negotiables / PRD gaps.

## Goal

Make mocapi explicitly conform to the **superpowers** spec-driven-development
pipeline as its standard way of working, and fill the four gaps around it —
a roadmap, a single source of architectural invariants, a refreshed PRD, and
documented context-hygiene for agentic work — **without** disturbing the
already-strong ADR + living-design-doc discipline.

## Context (current state)

The repo is already well set up for disciplined docs:

- `docs/adr/` — 25 ADRs with provenance notes, immutability-in-spirit,
  supersession protocol, status legend, template, and index.
- `docs/design/` — living architecture docs with an explicit sync rule
  ("if a design doc and an ADR disagree, the design doc is wrong — fix it").
- `docs/guides/` — user how-tos.
- `CLAUDE.md` (global + project) — conduct non-negotiables (no suppressed
  warnings, no star imports) and an "architecturally significant → ADR
  required" trigger list, plus lazy-loaded framework context.
- `docs/superpowers/plans/` — implementation plans (superpowers convention),
  already seeded with two cleanup plans.

**Gaps this design closes:**

1. No roadmap — "where we're going" is captured nowhere (ADRs are
   backward-looking by design).
2. Non-negotiables are scattered — conduct rules in CLAUDE.md, architectural
   invariants spread across ADR-0002/0019/0020/0022 and the ADR-trigger list.
   No single citable "constitution."
3. `PRD.md` is stale — still says "targets MCP 2025-11-25," lists the wrong
   module names (`mocapi-example`), and predates the clean break.
4. No documented SDD pipeline or context-hygiene norm for subagent-driven work.

## Decisions locked during brainstorming

- **Scope:** consolidate & fill gaps (low ceremony) — build on existing
  discipline, do not adopt a heavier prescriptive methodology.
- **Non-negotiables model:** split by concern — CLAUDE.md owns *conduct*
  rules; a new constitution owns *architectural invariants*, each linking its
  governing ADR. No duplication.
- **SDD backbone:** the superpowers plugin pipeline
  (`brainstorming → spec → writing-plans → plan → subagent-driven-development`).
- **Constitution location:** `docs/constitution.md`.
- **Legacy `docs/plans/`:** archive in place with a one-line note; no churn.
- **Roadmap:** scaffold near-term lanes from known work; leave the rest TBD
  for the maintainer to fill.
- **Execution default:** subagent-driven (inline where it fits).

## Design

### 1. The SDD pipeline, written down

Create `docs/superpowers/README.md` stating the standard flow and when it
applies, and add a pointer to it from `CONTRIBUTING.md`.

- **Non-trivial change** (new feature, new SPI, transport/contract change,
  anything in CLAUDE.md's "architecturally significant" trigger list):
  `brainstorming` (→ spec in `docs/superpowers/specs/`) → `writing-plans`
  (→ plan in `docs/superpowers/plans/`) → `subagent-driven-development`
  (default) with TDD, `verification-before-completion`, and
  `requesting-code-review` gates. Architecturally significant changes still
  produce an ADR + design-doc update per the existing CLAUDE.md rule; the
  spec references the ADR.
- **Trivial change** (typo, dependency bump, doc-only fix, mechanical
  refactor with no contract change): direct edit, no spec/plan required.
- **Execution mode:** subagent-driven by default; inline is the exception,
  recommended by the assistant where a task is small, tightly-coupled, or
  fast-iteration.

This README documents the flow only — it does not restate the skills'
internals (those live in the plugin).

### 2. Documentation map (one home per purpose)

| Home | Holds | Lifecycle |
|---|---|---|
| `docs/superpowers/specs/` | per-feature design specs (brainstorming output) | dated, point-in-time |
| `docs/superpowers/plans/` | per-feature implementation plans (writing-plans output) | dated, point-in-time |
| `docs/adr/` | architecture decisions | immutable-in-spirit, supersession protocol |
| `docs/design/` | living architecture | synced with code |
| `docs/guides/` | user how-tos | living |
| `docs/roadmap.md` *(new)* | forward direction | living |
| `docs/constitution.md` *(new)* | architectural invariants | living, links ADRs |
| `CLAUDE.md` | agent/contributor conduct rules | living |
| `PRD.md` | product north-star (what/why) | living, refreshed here |

**Legacy `docs/plans/`:** add a one-line note at the top of its migration
document marking it a pre-superpowers migration artifact. The MCP schema
snapshots (`2026-07-28-schema.ts/.json`) and the schema-diff **stay in place**
— they are reference material cited by ADRs and the migration doc. No files
move.

### 3. Constitution (`docs/constitution.md`)

A short, numbered, **citable** list of architectural invariants — the things a
change must not violate without a superseding ADR. Each invariant is one or
two sentences and links its governing ADR(s). It does **not** restate the
ADRs; it is an index of invariants. Candidate set (finalized during
implementation by reading the cited ADRs):

- **I1 — Stateless request model.** No sessions, no handshake, no
  server-initiated request channel. (ADR-0019, ADR-0020)
- **I2 — Single protocol/transport coupling.** `McpServer` ↔ `McpTransport`
  is the only seam; `mocapi-server` depends on no I/O framework. (ADR-0002)
- **I3 — Spec-compliance target.** mocapi tracks the current MCP revision
  (2026-07-28) with a clean-break philosophy; deprecated-at-introduction
  features are not adopted. (ADR-0019, ADR-0022)
- **I4 — Module boundaries & packaging.** (ADR-0001)
- **I5 — Static handler discovery.** Tools/prompts/resources are discovered
  at startup; no dynamic registration / list-changed push. (ADR-0010)
- **I6 — Declared not-implemented surface.** The canonical "does mocapi do X?"
  list. (ADR-0022)
- **I7 — Model is 1:1 with the MCP schema.** (ADR-0014)
- **I8 — Authorization model.** Bearer validation with mandatory audience
  enforcement; RFC 9728 metadata; Guard SPI composition. (ADR-0013, ADR-0012)
- **I9 — Error-code allocation.** mocapi-private codes live in the JSON-RPC
  implementation-defined sub-range (−32000..−32019); spec codes are used
  verbatim. (ADR-0023)

**CLAUDE.md change (conduct side):** add one pointer under a new short
"Architectural invariants" note — *"The load-bearing invariants live in
`docs/constitution.md`. Do not violate one without a superseding ADR."* — and
keep all conduct rules where they are.

### 4. Roadmap (`docs/roadmap.md`)

Lightweight and forward-looking, keyed to the 0.x cadence (minor = headline
features and possible pre-1.0 breaks; patch = hotfix). Structure: **Now /
Next / Later**, plus a "Guiding constraints" pointer to the constitution.

Near-term lanes scaffolded from known work (maintainer edits freely):

- **Now:** finalize MCP 2026-07-28 compliance — re-diff against the final
  released schema and re-pin the snapshot at spec release (migration plan
  Task 9.3); land the transport inbound-consistency cleanup
  (`docs/superpowers/plans/2026-07-17-transport-inbound-consistency.md`) and
  the ADR-0002 contract truth-up
  (`docs/superpowers/plans/2026-07-17-adr-0002-contract-truthup.md`).
- **Next:** Jakarta Validation via Methodical once it ships (annotate model
  records, map violations to JSON-RPC −32602); demo code + runner script for
  the stateless 2026-07-28 example server; resolve the deferred
  `HeaderMismatch`-into-`mocapi-model` ADR question.
- **Later (TBD — maintainer to fill):** path to 1.0; additional transports;
  extension-track stance (Tasks / MCP Apps) revisits.

Every "Later" bullet the maintainer has not confirmed is explicitly marked
**TBD** rather than invented.

### 5. PRD refresh (`PRD.md`)

Update to current reality and reduce drift risk by delegating volatile detail
to the living docs:

- Spec target: **MCP 2026-07-28** (was 2025-11-25).
- Correct the stack line and module names (no `mocapi-example`; the runnable
  apps are `mocapi-examples` / `mocapi-conformance`).
- Reframe as a stable one-page "what & why," linking out to `docs/roadmap.md`
  (where) and `docs/constitution.md` (invariants) instead of duplicating them.

### 6. Context hygiene for subagent-driven work

- **Subsystem read-map in CLAUDE.md:** extend the existing framework
  read-on-demand index to subsystems, e.g. *transports → `design/transports.md`
  + ADR-0002/0003/0004; auth → `design/authorization-model.md` +
  ADR-0012/0013; model → ADR-0014 + the schema snapshot; handlers/discovery →
  `design/handlers.md` + ADR-0010/0011*.
- **Dispatch norm:** document that every subagent dispatch names the specific
  design docs / ADRs / constitution invariants the task must honor, so a
  fresh-context subagent loads the right material (already done ad hoc; this
  makes it the rule, recorded in `docs/superpowers/README.md`).

## Out of scope

- Rewriting historical ADRs (0004/0008/0009) or the migration plan/diff — they
  are point-in-time records; falsifying history is not clean, it's lossy.
  An optional separate ADR-hygiene pass could add "Superseded by" back-links.
- Adopting a heavier prescriptive methodology (Spec Kit-style slash-command
  scaffolding) — explicitly declined in favor of consolidate-and-fill-gaps.
- Authoring the *content* of "Later" roadmap items — the maintainer owns
  product direction; this design only provides the home and format.

## Success criteria

1. A new contributor or agent can read `docs/superpowers/README.md` and know
   the exact spec → plan → implement flow and when it applies.
2. Every architectural invariant is findable in one citable place
   (`docs/constitution.md`), each linked to its governing ADR, with CLAUDE.md
   pointing to it.
3. `docs/roadmap.md` exists with accurate Now/Next lanes and clearly-marked
   TBD Later lanes.
4. `PRD.md` contains no stale spec version, stack, or module names.
5. CLAUDE.md's read-on-demand index covers the major subsystems, so
   subagent-driven tasks can self-load the right context.
6. No files churned unnecessarily; historical records preserved; existing
   ADR/design-doc discipline unchanged.

## Implementation note

This is a docs/process change. The one code-adjacent touch is editing
`CLAUDE.md` (conduct pointer + subsystem read-map). Per the maintainer's
"commit only when asked" rule, the resulting plan will stage changes but leave
committing to the maintainer. The implementation plan will be produced by the
`writing-plans` skill and executed subagent-driven.
