# SDD Workflow (superpowers)

mocapi uses the **superpowers** spec-driven-development pipeline (the
`brainstorming` → `writing-plans` → `subagent-driven-development` skills). This
file states **when** it applies and **where** artifacts live; the skills
themselves define the **how**.

## The flow

A non-trivial change flows through four stages:

1. **`brainstorming`** → a design spec in [`specs/`](specs/)
   (`YYYY-MM-DD-<topic>-design.md`).
2. **`writing-plans`** → an implementation plan in [`plans/`](plans/)
   (`YYYY-MM-DD-<feature>.md`), broken into bite-sized, independently
   reviewable tasks.
3. **`subagent-driven-development`** (default) or **`executing-plans`** (inline)
   → the plan is implemented task-by-task with TDD,
   `verification-before-completion`, and `requesting-code-review` gates.
4. If the change is architecturally significant (see the trigger list in
   [`../../CLAUDE.md`](../../CLAUDE.md)), it also produces an **ADR** plus a
   **design-doc** update in the same change; the spec references the ADR.

## When it applies

- **Non-trivial** — a new feature, a new or changed SPI, a transport/contract
  change, or anything on CLAUDE.md's "architecturally significant" list: run
  the full pipeline.
- **Trivial** — a typo, a dependency bump, a doc-only fix, or a mechanical
  refactor with no contract change: direct edit, no spec/plan.

## Execution mode

**Subagent-driven by default.** Inline (`executing-plans`) is the exception,
appropriate where a task set is small, tightly-coupled, or fast-iteration.

## Context hygiene

Every subagent dispatch names the specific design docs, ADRs, and
[constitution](../constitution.md) invariants the task must honor, so a
fresh-context worker loads the right material. See CLAUDE.md's
"Read-before-you-touch" subsystem map.

## Spec vs ADR — when each

A spec (`specs/`) is a **build document**: it captures intent for one
feature at the moment it's being built, then goes stale once the
feature ships — it is not maintained afterward.

An ADR is the durable, citable, supersedable **why**. Write one only
when a change creates or alters an invariant (see
[constitution.md](../constitution.md)), changes a public contract
(SPI, transport, capability), or establishes a pattern other code must
follow. Most feature specs do **not** produce an ADR — only the subset
that meets CLAUDE.md's "architecturally significant" bar does. When in
doubt, ask before writing code rather than skip the ADR.

## Where things live

| Directory | Holds |
|---|---|
| [`specs/`](specs/) | design specs (brainstorming output) |
| [`plans/`](plans/) | implementation plans (writing-plans output) |
| [`../adr/`](../adr/) | architecture decisions |
| [`../design/`](../design/) | living architecture docs |
| [`../guides/`](../guides/) | user how-tos |
