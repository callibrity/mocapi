# ADR-0002 Contract Truth-Up — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the canonical protocol/transport-contract ADR describe the interface that actually exists, without rewriting history, so a reader who opens ADR-0002 doesn't see a dead API.

**Architecture:** Documentation only — no code changes. ADR-0002 records a 2025-07-09 decision and shows an `McpServer`/`McpTransport`/`McpEvent` interface with `createContext`, `handleResponse`, `terminate`, and `SessionInitialized`. The stateless clean break (ADR-0019) and stateless-request model (ADR-0020) removed all of those: the live contract is now `McpServer.handleCall` + `handleNotification` and `McpTransport.send` only; `McpEvent.java` no longer exists. Per this repo's doc philosophy — `docs/adr/` is point-in-time, `docs/design/` is living and must never describe behavior that is no longer true — the fix is a dated update note on the ADR (the *principle* still governs) plus a check that the living design docs are already correct.

**Tech Stack:** Markdown docs only.

## Global Constraints

- `docs/adr/` entries are point-in-time records; do **not** silently rewrite a past decision's narrative. Corrections go in a clearly dated update note.
- `docs/design/` entries are living and must reflect current behavior exactly.
- The *decision principle* of ADR-0002 (the two-interface contract is the only coupling between protocol and transport; the core depends on no I/O framework) is still true and is NOT being reversed — only the interface *shape* shown in the code block is stale.

---

## ⚠ Decision Point (resolve during review)

**How to treat the stale interface in ADR-0002.** The plan implements **Option A** (recommended): keep the original decision text as the historical record and add a dated update note + a corrected "current contract" code block, since the principle still holds and only the method set changed. **Option B** would instead flip ADR-0002 to `Superseded by ADR-0020` and write the current contract solely in the living design doc. Option A keeps the citable contract ADR useful and matches how ADR-0022/0023 were handled (dated notes, status retained); Option B is heavier and loses the single canonical contract page. If you prefer B, replace Task 1 with a one-line status flip and move the corrected code block into `docs/design/architecture-overview.md`.

---

### Task 1: Add the dated update note + corrected contract to ADR-0002

**Files:**
- Modify: `docs/adr/0002-protocol-transport-contract.md`

- [ ] **Step 1: Insert an update note directly under the status/date header**

After the `- **Date:** 2025-07-09` line, add:

```markdown
> **Update (2026-07-17):** The interface shapes shown below are the original
> 2025-07-09 forms. The stateless clean break ([ADR-0019](0019-clean-break-2026-07-28.md))
> and stateless-request model ([ADR-0020](0020-stateless-request-model.md)) removed
> `createContext`, `handleResponse`, `terminate`, and the entire `McpEvent` type
> (there is no session, no server-initiated request channel, and no lifecycle
> event). The **decision itself stands** — the two-interface split is still the
> only coupling between protocol and transport, and the core still depends on no
> I/O framework. Only the method set changed. The current contract is:
>
> ```java
> public interface McpServer {
>     void handleCall(JsonRpcCall call, McpTransport transport);
>     void handleNotification(JsonRpcNotification notification);
> }
>
> public interface McpTransport {
>     void send(JsonRpcMessage message);
> }
> ```
>
> Rules 2 and 5 below (session/protocol-version validation via `createContext`;
> client responses routed to `handleResponse`/Mailbox) no longer apply: there is
> no session to validate and no response channel. Rule 3 (transport-agnostic
> core) and rule 4 (server-agnostic transports) are unchanged and still govern.
```

- [ ] **Step 2: Fix the Code anchors line**

The `**Code anchors:**` line references `McpEvent.java`, which no longer exists. Change it to:

```markdown
**Code anchors:** `mocapi-server/.../McpServer.java`, `McpTransport.java`.
```

- [ ] **Step 3: Verify the ADR reads coherently**

Read the file top-to-bottom. The update note must appear before the stale code block so a reader hits the correction first. No other edits to the historical body.

- [ ] **Step 4: Commit**

```bash
git add docs/adr/0002-protocol-transport-contract.md
git commit -m "docs(adr-0002): note stateless clean break reshaped the transport contract [skip ci]"
```

> Note: `[skip ci]` only applies if the commit touches docs exclusively. If your workflow batches this with code, drop it.

---

### Task 2: Confirm the living design docs are already correct

**Files:**
- Verify (no edit expected): `docs/design/architecture-overview.md`, `docs/design/transports.md`, `docs/design/handlers.md`

**Context:** The living design docs are the ones that *must* be current. An audit grep found none of the removed-surface terms (`createContext`, `handleResponse`, `SessionInitialized`, `McpEvent`, `McpContextResult`) in `docs/design/`, so this task is a guard, not an edit.

- [ ] **Step 1: Grep the living docs for dead-surface references**

Run:

```bash
grep -rn "createContext\|handleResponse\|SessionInitialized\|McpEvent\|McpContextResult\|transport.emit" docs/design/
```

Expected: no matches.

- [ ] **Step 2: If any match is found, fix it**

For each hit, rewrite the sentence to the current two-method contract (`handleCall`/`handleNotification`/`send`). If there are zero hits (expected), skip to Step 3.

- [ ] **Step 3: Commit only if something changed**

```bash
git add docs/design/
git commit -m "docs(design): remove stale transport-contract references [skip ci]"
```

If Step 1 found nothing, record that in the review notes and make no commit.

---

## Out of scope (intentionally not touched)

- **Historical ADRs 0004, 0008, 0009** and `docs/plans/2026-06-11-mcp-2026-07-28-migration.md` also mention the old surface, but they are point-in-time records of decisions made *while that surface existed*. Rewriting them would falsify history. ADR-0008 (Mailbox) is already effectively obsolete under the stateless model; if you want, a *separate* pass could add "Superseded by ADR-0020" back-links to 0004/0008/0009 — but that is a distinct ADR-hygiene task, not part of this truth-up.

## Self-Review

- **Coverage:** the one stale living-contract artifact (ADR-0002) is corrected (Task 1); living design docs are verified clean (Task 2); history is preserved (Out of scope).
- **Placeholders:** none — the exact note text and anchor replacement are given verbatim.
- **Consistency:** the corrected code block matches the real interfaces confirmed in source (`McpServer.handleCall`/`handleNotification`, `McpTransport.send`; `McpEvent` removed).
