# MCP 2026-07-28 schema diff — mocapi-model migration gate

**Status:** Authoritative analysis artifact for Plan Task 1.1
(`docs/plans/2026-06-11-mcp-2026-07-28-migration.md`). This document gates
all Phase 1 model-layer code changes (Tasks 1.2–1.6).

**Schema source (pinned RC snapshot):**

- Fetched 2026-06-11 from
  `https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/main/schema/draft/schema.ts`
  (and the sibling `schema.json`).
- Last commit touching `schema/draft/schema.ts` at fetch time:
  `77cb26481e439d3437bc2bd6ccd19fcae86bb1ec` (2026-06-07, "fix(schema):
  extract ElicitationCompleteNotificationParams to extend NotificationParams (#2866)").
- Snapshots committed alongside this doc: `docs/plans/2026-07-28-schema.ts`,
  `docs/plans/2026-07-28-schema.json`. Re-diff against the final release
  after 2026-07-28 (Plan Task 9.3).
- Sanity check passed: `LATEST_PROTOCOL_VERSION = "2026-07-28"`; the schema
  contains `server/discover` and `InputRequiredResult` and contains **no**
  `InitializeRequest`, `ping`, `logging/setLevel`, `resources/subscribe`,
  `resources/unsubscribe`, `notifications/initialized`,
  `notifications/roots/list_changed`, or task types.

**Spec prose consulted** (fetched 2026-06-11):
`/specification/draft/basic/index` (`_meta` rules),
`/specification/draft/basic/versioning`, `/specification/draft/server/discover`.

**Baseline:** the 94 classes in
`mocapi-model/src/main/java/com/callibrity/mocapi/model/` (1:1 translation
of the 2025-11-25 `schema.ts` per ADR-0014). mocapi's modeling convention:
params types, result types, and shared data types are modeled; JSON-RPC
envelope wrappers (`JSONRPCRequest`, `*Response`) are not, except
`JsonRpcError` and `ProgressNotification`. That convention is unchanged.

## Headline counts

| Category  | Count | Notes |
|-----------|-------|-------|
| DELETE    | 5     | classes removed outright |
| DEPRECATE | 12    | stay 1:1 with `@Deprecated`; nothing outside `mocapi-model` references them |
| MODIFY    | 25    | survive with field-level deltas (3 of them are spec-side-only, no Java change) |
| CREATE    | 12 (+1 constants class, +7 decision-gated) | see open decision D1 |
| UNCHANGED | 52    | listed at the end |

Total current classes: 5 + 12 + 25 + 52 = 94. ✓

---

## 1. DELETE — types removed from the spec

Verified individually against the snapshot: none of these names (or their
RPC methods) appear anywhere in the draft schema.

| Class | Removed mechanism | Replacement |
|---|---|---|
| `InitializeRequestParams` | `initialize` RPC removed (SEP-2575) | per-request `_meta` envelope + `server/discover` |
| `InitializeResult` | same | `DiscoverResult` |
| `InitializedNotificationParams` | `notifications/initialized` removed | — (no handshake) |
| `SetLevelRequestParams` | `logging/setLevel` RPC removed | `io.modelcontextprotocol/logLevel` `_meta` key (itself deprecated) |
| `TaskMetadata` | Tasks moved out of core into the `io.modelcontextprotocol/tasks` extension (SEP-2663) | — (mocapi does not implement the extension, ADR-0022) |

Notes on plan Task 1.2's candidate list:

- **ping:** the `ping` method is gone from the spec. mocapi has no dedicated
  ping params/result class — only the `McpMethods.PING` constant and server
  handler. Delete the constant (see §6).
- **roots/list_changed notification params:** mocapi never had a dedicated
  class (generic `NotificationParams` was used). Only the
  `McpMethods.NOTIFICATIONS_ROOTS_LIST_CHANGED` constant goes.
- **subscribe/unsubscribe types:** `resources/subscribe` and
  `resources/unsubscribe` RPCs are removed (replaced by
  `subscriptions/listen`), but mocapi's `ResourceRequestParams` class is
  shared with `resources/read` and **survives** (see MODIFY). Only the two
  `McpMethods` constants go.

---

## 2. DEPRECATE — types remaining in schema.ts with `@deprecated` JSDoc

All carry the identical JSDoc tag: *"@deprecated Deprecated as of protocol
version 2026-07-28 (SEP-2577). Remains in the specification for at least
twelve months; see the deprecated features registry."* Per ADR-0019/0014
these stay in `mocapi-model` with `@Deprecated`; round-trip tests use the
spec-contract `@SuppressWarnings("deprecation")` rule. Nothing in
`mocapi-api`/`mocapi-server` may reference them.

### Sampling family (all carry `@deprecated` in schema.ts)

| Class | Field deltas while deprecating |
|---|---|
| `CreateMessageRequestParams` | drop `task` (`TaskMetadata`, deleted) and `meta` (`_meta`): in the draft, `CreateMessageRequestParams` is an *embedded* object inside `InputRequests` — it no longer extends `RequestParams` and has **no `_meta`** member. `includeContext` values `"thisServer"`/`"allServers"` separately deprecated (SEP-2596). |
| `CreateMessageResult` | schema shape: extends `SamplingMessage` + `model: string`, `stopReason?`. Note `SamplingMessage.content` is now `block \| block[]` and carries `_meta?`. mocapi's flat `(role, content, model, stopReason)` is acceptable for a deprecated, unimplemented type — but it is an `InputResponse` union member (see D1). |
| `SamplingMessage` | schema: `content: SamplingMessageContentBlock \| SamplingMessageContentBlock[]` (was single `ContentBlock`), `_meta?`. The content union includes `ToolUseContent`/`ToolResultContent`, which mocapi has never modeled (see D1). |
| `ModelPreferences` | no field changes |
| `ModelHint` | no field changes |
| `ToolChoice` | **shape mismatch**: schema `ToolChoice` is `{ mode?: "auto" \| "required" \| "none" }`. mocapi's sealed `Auto`/`None`/`Specific` (named-tool) model does not match — `Specific` and the `"tool"` payload form are not in the draft, and `"required"` is missing. Since deprecated and unimplemented, align to the spec shape (a record with a `mode` string) when annotating, or leave as-is with a fidelity note. Recommendation: align — 1:1 means 1:1. |
| `IncludeContext` (enum) | the `includeContext` field survives only inside deprecated `CreateMessageRequestParams`; values `THIS_SERVER`/`ALL_SERVERS` doubly deprecated (SEP-2596). |
| `SamplingCapability` | `ClientCapabilities.sampling` carries `@deprecated`. Schema shape gained `context?: {}` / `tools?: {}` sub-objects (2025-11-25-era; mocapi's empty record was already a simplification). |

### Roots family

| Class | Field deltas while deprecating |
|---|---|
| `RootsCapability` | `ClientCapabilities.roots` carries `@deprecated` and is now an **empty object `{}`** — `listChanged` is gone (the `notifications/roots/list_changed` notification no longer exists). Drop the `listChanged` component. |

(`Root`, `ListRootsRequest`, `ListRootsResult` were never modeled by mocapi;
they reappear as decision-gated CREATEs because the MRTR `InputRequest`/
`InputResponse` unions reference them — see D1.)

### Logging family

| Class | Field deltas while deprecating |
|---|---|
| `LoggingCapability` | `ServerCapabilities.logging` carries `@deprecated`. No shape change (`JSONObject`). |
| `LoggingLevel` | type alias carries `@deprecated`. Still referenced by the (deprecated) `io.modelcontextprotocol/logLevel` `_meta` key. No shape change. |
| `LoggingMessageNotificationParams` | carries `@deprecated`. No shape change. The client now opts in via the `_meta` `logLevel` key instead of `logging/setLevel`. |

Also deprecated in schema.ts but **not currently modeled** by mocapi:
`ListRootsRequest`, `ListRootsResult`, `Root`, `CreateMessageRequest`,
`SamplingMessageContentBlock`, `ToolUseContent`, `ToolResultContent` — see
open decision D1 under CREATE.

---

## 3. MODIFY — types surviving with field changes

### 3.1 The `_meta` envelope (root cause of most params changes)

`RequestMeta` becomes the spec's `RequestMetaObject`:

```ts
export interface RequestMetaObject extends MetaObject {
  progressToken?: ProgressToken;                                    // unchanged
  "io.modelcontextprotocol/protocolVersion": string;                // REQUIRED
  "io.modelcontextprotocol/clientInfo": Implementation;             // REQUIRED
  "io.modelcontextprotocol/clientCapabilities": ClientCapabilities; // REQUIRED
  /** @deprecated (SEP-2577) */
  "io.modelcontextprotocol/logLevel"?: LoggingLevel;                // optional, deprecated
}
```

`schema.json` confirms `required: ["io.modelcontextprotocol/clientCapabilities",
"io.modelcontextprotocol/clientInfo", "io.modelcontextprotocol/protocolVersion"]`.
A request missing any required field is malformed → JSON-RPC `-32602`
(Invalid params); on HTTP additionally `400 Bad Request` (spec
`basic/index#meta`, "Per-request protocol fields"). This matches plan Task 2.1.

| Class | Delta |
|---|---|
| `RequestMeta` | add the three required `io.modelcontextprotocol/*` components + optional deprecated `logLevel`. Keep `progressToken`. (Rename to `RequestMetaObject` to track the spec name, or keep `RequestMeta` — naming is Task 1.3/1.4's call; spec name is `RequestMetaObject`.) |
| `RequestParams` | `_meta` is now **required** (`schema.json`: `required: ["_meta"]`). |
| `PaginatedRequestParams` | same — `_meta` required; `cursor` unchanged. |

### 3.2 Request params gaining the MRTR retry envelope

The draft introduces `InputResponseRequestParams` (extends `RequestParams`):

```ts
/* Request parameter type that includes input responses and request state.
 * These parameters may be included in any client-initiated request.
 */
export interface InputResponseRequestParams extends RequestParams {
  /* New field to carry the responses for the server's requests from the
   * InputRequiredResult message.  For each key in the response's inputRequests
   * field, the same key must appear here with the associated response.
   */
  inputResponses?: InputResponses;
  /* Request state passed back to the server from the client.
   */
  requestState?: string;
}
```

Exactly **three** RPC params types extend it: `CallToolRequestParams`,
`GetPromptRequestParams`, `ReadResourceRequestParams`. (`CompleteRequestParams`,
`PaginatedRequestParams`, `SubscriptionsListenRequestParams`, and
`DiscoverRequest`'s params do **not** — those methods cannot return
`input_required`.) The retried request is the **same method with the same
id-semantics as any fresh request**: the client re-sends the original params
plus `inputResponses` (keyed identically to the server's `inputRequests`
map) and the opaque `requestState` string.

| Class | Delta |
|---|---|
| `CallToolRequestParams` | drop `task` (deleted); add `inputResponses: Map<String, InputResponse>` and `requestState: String`. `name`/`arguments` unchanged. |
| `GetPromptRequestParams` | add `inputResponses`, `requestState`. `name`/`arguments` unchanged. |
| `ResourceRequestParams` | the draft splits this: `ResourceRequestParams` (internal: `uri` + `_meta`) and `ReadResourceRequestParams extends ResourceRequestParams, InputResponseRequestParams {}`. mocapi's single class now serves only `resources/read` (subscribe/unsubscribe are gone) → add `inputResponses`, `requestState` (or introduce `ReadResourceRequestParams` to track spec naming). |

### 3.3 Results: required `resultType` on every result (NOT in the plan — see surprise S2)

The base `Result` interface adds a **required** field:

```ts
export type ResultType = "complete" | "input_required" | string;

export interface Result {
  _meta?: MetaObject;
  resultType: ResultType;   // REQUIRED — "Servers implementing this protocol
                            // version MUST include this field."
  [key: string]: unknown;
}
```

Every result type mocapi serializes must emit `resultType: "complete"`
(or `"input_required"` for `InputRequiredResult`). Affected classes:
`CallToolResult`, `GetPromptResult`, `CompleteResult`, `EmptyResult`, the
five list/read results below, and the new `DiscoverResult`/`InputRequiredResult`.
Note `ElicitResult` is **not** affected — in the draft it no longer extends
`Result` (it is an embedded `InputResponse`, not an RPC result).

| Class | Delta |
|---|---|
| `CallToolResult` | `structuredContent` widens `ObjectNode` → any JSON value (`JsonNode`): *"This can be any JSON value (object, array, string, number, boolean, or null)"*. Add required `resultType`. `content`/`isError` unchanged. |
| `GetPromptResult` | add required `resultType`. Not cacheable (no ttl fields). |
| `CompleteResult` | add required `resultType`. |
| `EmptyResult` | add required `resultType` (`EmptyResult = Result`). The `INSTANCE` singleton must carry `"complete"`. |

### 3.4 Cacheable results — `ttlMs` + `cacheScope` required

```ts
export interface CacheableResult extends Result {
  /** ... Semantics analogous to HTTP Cache-Control max-age. @minimum 0 */
  ttlMs: number;                       // REQUIRED (schema.json: integer, min 0)
  /** "public" | "private" — analogous to Cache-Control public/private */
  cacheScope: "public" | "private";    // REQUIRED
}
```

`schema.json`: `required: ["cacheScope", "resultType", "ttlMs"]`. Exact
names/types confirmed: `ttlMs` is **integer** (minimum 0) in schema.json;
`cacheScope` is the closed enum `"public" | "private"`.

There are **six** cacheable results in the draft, not five (surprise S3):
`ListToolsResult`, `ListPromptsResult`, `ListResourcesResult`,
`ListResourceTemplatesResult`, `ReadResourceResult`, **and `DiscoverResult`**.

| Class | Delta |
|---|---|
| `ListToolsResult` | + required `ttlMs` (long), `cacheScope`; + required `resultType`; `tools`/`nextCursor` unchanged. |
| `ListPromptsResult` | same pattern. |
| `ListResourcesResult` | same pattern. |
| `ListResourceTemplatesResult` | same pattern. |
| `ReadResourceResult` | + required `ttlMs`, `cacheScope`, `resultType`; `contents` unchanged (`TextResourceContents \| BlobResourceContents`). |

### 3.5 Capabilities

| Class | Delta |
|---|---|
| `ClientCapabilities` | add `experimental?: Map<String, ObjectNode>` (pre-existing 2025-11-25 gap) and **new** `extensions?: Map<String, ObjectNode>` (keys follow `_meta` naming rules with mandatory prefix). `roots` and `sampling` members carry `@deprecated`. `elicitation` is `{ form?: {}, url?: {} }` (see `ElicitationCapability`). |
| `ServerCapabilities` | add `experimental?` (pre-existing gap) and **new** `extensions?: Map<String, ObjectNode>`. `logging` member carries `@deprecated`. `prompts.listChanged` / `resources.subscribe` / `resources.listChanged` / `tools.listChanged` all **remain** in the draft (they now describe what `subscriptions/listen` can deliver). |
| `ElicitationCapability` | gains `form?: JSONObject` and `url?: JSONObject` sub-objects. *"form mode only (implicit)"* example shows `{}` is valid (form support implicit). mocapi's `McpExchange.supportsElicitationForm()` must treat `elicitation: {}` as form-capable. |

Exact draft capability shapes (JSDoc trimmed):

```ts
export interface ClientCapabilities {
  experimental?: { [key: string]: JSONObject };
  /** @deprecated (SEP-2577) */ roots?: {};
  /** @deprecated (SEP-2577) */ sampling?: { context?: JSONObject; tools?: JSONObject };
  elicitation?: { form?: JSONObject; url?: JSONObject };
  extensions?: { [key: string]: JSONObject };
}

export interface ServerCapabilities {
  experimental?: { [key: string]: JSONObject };
  /** @deprecated (SEP-2577) */ logging?: JSONObject;
  completions?: JSONObject;
  prompts?: { listChanged?: boolean };
  resources?: { subscribe?: boolean; listChanged?: boolean };
  tools?: { listChanged?: boolean };
  extensions?: { [key: string]: JSONObject };
}
```

### 3.6 Elicitation params (now embedded objects, not RPC params)

| Class | Delta |
|---|---|
| `ElicitRequestFormParams` | drop `task` and `meta` — in the draft these params live inside an embedded `ElicitRequest` within `InputRequests`; they do not extend `RequestParams` and carry no `_meta`. `mode?: "form"` (optional). `requestedSchema` gains optional `$schema`. |
| `ElicitRequestURLParams` | drop `task` and `meta`. `mode: "url"` (required discriminator). `message`/`elicitationId`/`url` unchanged. (URL mode stays declared-not-implemented, ADR-0022.) |
| `RequestedSchema` | gains optional `$schema?: string` alongside `type: "object"`, `properties`, `required`. Flat-properties restriction unchanged (ADR-0015 builder unaffected). |

### 3.7 Misc

| Class | Delta |
|---|---|
| `Implementation` | target draft shape: `name` (req), `version` (req), `title?`, `description?`, `websiteUrl?`, `icons?`. mocapi has `name`/`title`/`version` only — `websiteUrl`/`icons` are pre-existing 2025-11-25 gaps; `description?` appears in the draft. As `clientInfo` it must deserialize leniently; only `name`+`version` are required. |
| `McpMethods` | see §6 for the full post-migration inventory. |
| `CancelledNotificationParams` | **spec-side only**: `requestId` is now optional (`requestId?: RequestId`). mocapi's record already permits null — no Java change; relax any server-side validation that required it. |
| `NumberSchema` | **spec-side only**: the number-vs-integer fix landed — `minimum`/`maximum`/`default` are `"type": "number"` in the draft schema.json (were `integer`). mocapi already uses `Number` — no Java change. |
| `ProgressNotificationParams` | spec-side only: `progress`/`total` explicitly `number` (`@TJS-type number`). mocapi already `double`/`Double` — no change. Listed here for completeness; counted UNCHANGED. |

---

## 4. CREATE — new types

### 4.1 `DiscoverRequest` / `DiscoverResult` (`server/discover`)

Verbatim from the snapshot (JSDoc examples elided):

```ts
/**
 * A request from the client asking the server to advertise its supported
 * protocol versions, capabilities, and other metadata. Servers **MUST**
 * implement `server/discover`. Clients **MAY** call it but are not required
 * to — version negotiation can also happen inline via per-request `_meta`.
 */
export interface DiscoverRequest extends JSONRPCRequest {
  method: "server/discover";
  params: RequestParams;          // ← params REQUIRED, and RequestParams._meta REQUIRED
}

export interface DiscoverResult extends CacheableResult {
  /** MCP Protocol Versions this server supports. ... */
  supportedVersions: string[];    // REQUIRED
  /** The capabilities of the server. */
  capabilities: ServerCapabilities;   // REQUIRED
  /** Information about the server software implementation. */
  serverInfo: Implementation;     // REQUIRED
  /** Natural-language guidance describing the server and its features. ... */
  instructions?: string;
}
```

`schema.json` `DiscoverRequest.required = ["id","jsonrpc","method","params"]`;
`DiscoverResult.required = ["cacheScope","capabilities","resultType","serverInfo","supportedVersions","ttlMs"]`.

No dedicated params type is needed — `params` is plain `RequestParams`
(the `_meta` envelope only).

**The `server/discover` `_meta` question — ANSWERED (against plan Task 2.3's
assumption):** the envelope is **required**. The schema makes `params`
required on `DiscoverRequest` and `_meta` required on `RequestParams`, with
all three `io.modelcontextprotocol/*` keys required inside it. The spec's
discover page confirms: *"The request carries no body parameters beyond the
standard `_meta`"* — and its example carries the full envelope. There is no
"no-envelope probe" carve-out. The version-unknown bootstrap works because a
discover request carrying an **unsupported** version gets
`UnsupportedProtocolVersionError`, whose `data.supported` lists the
server's versions — the client learns the answer either way (versioning
page: *"a recognized modern JSON-RPC error ... identifies a modern server:
the client retries with a supported version"*). **Consequence for Task 2.3:**
`DiscoverHandler` must require and parse the envelope like every other
handler; a missing envelope → `-32602`; an unsupported version in the
envelope → `UnsupportedProtocolVersionError` (still HTTP 400 + supported
list, which serves as the probe response).

### 4.2 MRTR family: `InputRequiredResult`, `InputRequest(s)`, `InputResponse(s)`

Verbatim from the snapshot:

```ts
/** @internal */
export type InputRequest =
  | CreateMessageRequest
  | ListRootsRequest
  | ElicitRequest;

/** @internal */
export type InputResponse =
  | CreateMessageResult
  | ListRootsResult
  | ElicitResult;

/**
 * A map of server-initiated requests that the client must fulfill.
 * Keys are server-assigned identifiers; values are the request objects.
 */
export interface InputRequests {
  [key: string]: InputRequest;
}

/**
 * A map of client responses to server-initiated requests.
 * Keys correspond to the keys in the {@link InputRequests} map;
 * values are the client's result for each request.
 */
export interface InputResponses {
  [key: string]: InputResponse;
}

/**
 * An InputRequiredResult sent by the server to indicate that additional input is needed
 * before the request can be completed.
 *
 * At least one of `inputRequests` or `requestState` MUST be present.
 */
export interface InputRequiredResult extends Result {
  /* Requests issued by the server that must be complete before the
   * client can retry the original request.
   */
  inputRequests?: InputRequests;
  /* Request state to be passed back to the server when the client
   * retries the original request.
   * Note: The client must treat this as an opaque blob; it must not
   * interpret it in any way.
   */
  requestState?: string;
}
```

`schema.json` `InputRequiredResult.required = ["resultType"]` — its
`resultType` is `"input_required"`. The "requestState only" variant exists
for load shedding (spec example: `input-required-result-with-request-state-only.json`).

**Retry envelope (how `inputResponses` + `requestState` travel):** via
`InputResponseRequestParams` quoted in §3.2 — the client retries the
*original method* with the original params plus `inputResponses` (same keys
as the server's `inputRequests`) and the opaque `requestState`. Only
`tools/call`, `prompts/get`, `resources/read` params carry these fields, and
correspondingly only `CallToolResultResponse`, `GetPromptResultResponse`,
and `ReadResourceResultResponse` declare `result: X | InputRequiredResult`.

The embedded request envelope (the only union member mocapi emits):

```ts
export interface ElicitRequest {
  method: "elicitation/create";
  params: ElicitRequestParams;     // ElicitRequestFormParams | ElicitRequestURLParams
}
```

Union discrimination: `InputRequest` members are discriminated by their
`method` literal (`"sampling/createMessage"` / `"roots/list"` /
`"elicitation/create"`). `InputResponse` members have **no discriminator
property** — `schema.json` uses a bare `anyOf` (deduction-based: `action`
⇒ `ElicitResult`, `roots` ⇒ `ListRootsResult`, `role`+`content`+`model`
⇒ `CreateMessageResult`).

Java types to create: `InputRequiredResult` (record), `ElicitRequest`
(envelope record), `InputRequest` (sealed interface), `InputResponse`
(sealed interface; make `ElicitResult` implement it). The `InputRequests`/
`InputResponses` maps need no dedicated classes — `Map<String, InputRequest>`
/ `Map<String, InputResponse>` suffice.

### 4.3 `CacheScope`

New closed enum from `CacheableResult.cacheScope`: `"public" | "private"`.
Create a `CacheScope` enum (wire values lowercase).

### 4.4 `ResultType`

`type ResultType = "complete" | "input_required" | string` — an **open**
string union. Model as a `String` record component plus constants
(`"complete"`, `"input_required"`), not a closed enum.

### 4.5 New error shapes

The schema defines **two** new MCP error codes and shapes (both modeled in
the schema as complete JSON-RPC error *responses*, not bare error objects):

```ts
export const MISSING_REQUIRED_CLIENT_CAPABILITY = -32003;
export const UNSUPPORTED_PROTOCOL_VERSION = -32004;

/**
 * Returned when the request's protocol version is unknown to the server or
 * unsupported (e.g., a known experimental or draft version the server has
 * chosen not to implement). For HTTP, the response status code MUST be
 * `400 Bad Request`.
 */
export interface UnsupportedProtocolVersionError extends Omit<
  JSONRPCErrorResponse,
  "error"
> {
  error: Error & {
    code: typeof UNSUPPORTED_PROTOCOL_VERSION;
    data: {
      /**
       * Protocol versions the server supports. The client should choose a
       * mutually supported version from this list and retry.
       */
      supported: string[];
      /**
       * The protocol version that was requested by the client.
       */
      requested: string;
    };
  };
}

/**
 * Returned when processing a request requires a capability the client did not
 * declare in `clientCapabilities`. For HTTP, the response status code MUST be
 * `400 Bad Request`.
 */
export interface MissingRequiredClientCapabilityError extends Omit<
  JSONRPCErrorResponse,
  "error"
> {
  error: Error & {
    code: typeof MISSING_REQUIRED_CLIENT_CAPABILITY;
    data: {
      /**
       * The capabilities the server requires from the client to process this request.
       */
      requiredCapabilities: ClientCapabilities;
    };
  };
}
```

`schema.json` confirms `data.required = ["requested","supported"]` /
`["requiredCapabilities"]`. Java: model the two `data` payload records (e.g.
`UnsupportedProtocolVersionErrorData(List<String> supported, String requested)`,
`MissingRequiredClientCapabilityErrorData(ClientCapabilities requiredCapabilities)`)
plus code constants `-32003`/`-32004`; the JSON-RPC envelope continues to go
through `JsonRpcError`.

**`HeaderMismatch` is NOT in the schema.** No `-32001` constant or type
exists in schema.ts/schema.json — it lives only in the Streamable HTTP
transport prose (plan Task 3.1 already pins it from there). Define the
`-32001` constant transport-side, not in `mocapi-model`. The schema also
notes the standard `-32601` MethodNotFound covers capability-gated methods
the *server* didn't advertise, while `-32003` covers capabilities the
*client* didn't declare.

### 4.6 Subscriptions family (model-only; feature declared not implemented, ADR-0022)

```ts
export interface SubscriptionFilter {
  toolsListChanged?: boolean;
  promptsListChanged?: boolean;
  resourcesListChanged?: boolean;
  /** Subscribe to notifications/resources/updated for these resource URIs.
   *  Replaces the former `resources/subscribe` RPC. */
  resourceSubscriptions?: string[];
}

export interface SubscriptionsListenRequestParams extends RequestParams {
  notifications: SubscriptionFilter;    // REQUIRED
}

export interface SubscriptionsAcknowledgedNotificationParams extends NotificationParams {
  /** The subset of requested notification types the server agreed to honor. */
  notifications: SubscriptionFilter;    // REQUIRED
}
```

Create all three for 1:1 fidelity; mocapi answers `subscriptions/listen`
with `-32601` (method not found — capability not advertised), per ADR-0022.

### 4.7 mocapi-specific constants class (plan Task 1.3)

`McpMetaKeys` — see §5 for the authoritative key list.

### D1 — Open decision: deprecated union members never previously modeled

Strict 1:1 fidelity for the `InputRequest`/`InputResponse` unions requires
**seven** types mocapi has never had, all `@deprecated` in the schema:
`CreateMessageRequest` (envelope), `ListRootsRequest`, `ListRootsResult`,
`Root`, `SamplingMessageContentBlock`, `ToolUseContent`, `ToolResultContent`.

- **For creating them:** ADR-0014's 1:1 contract; the sealed
  `InputRequest`/`InputResponse` Java unions would otherwise diverge from the
  spec unions; a client could legally send a `CreateMessageResult` or
  `ListRootsResult` in `inputResponses` (mocapi would never have asked, but
  the wire shape permits it and deserialization must not blow up with an
  obscure Jackson error).
- **Against:** writing *new* deprecated code contradicts the clean-break
  spirit (ADR-0019 says deprecated types *stay*, which presumes existence);
  mocapi emits only `ElicitRequest` and consumes only `ElicitResult`.

**Recommendation:** create them as minimal `@Deprecated` records (no
convenience methods, round-trip tests only) so the unions are spec-complete
and unknown-variant deserialization fails with a clear, typed error.
Resolve before Task 1.4. If declined, the sealed unions must document the
divergence and dispatch must map non-elicitation `inputResponses` entries to
`-32602`.

---

## 5. `_meta` key constants (spec-defined)

From the snapshot's `RequestMetaObject` + spec `basic/index#meta`:

| Constant | Key string | Where | Required |
|---|---|---|---|
| protocol version | `io.modelcontextprotocol/protocolVersion` | every client request `_meta` | yes |
| client info | `io.modelcontextprotocol/clientInfo` | every client request `_meta` | yes |
| client capabilities | `io.modelcontextprotocol/clientCapabilities` | every client request `_meta` | yes |
| log level | `io.modelcontextprotocol/logLevel` | client request `_meta` | no — **deprecated** (SEP-2577); mocapi omits per plan Task 1.3 |
| progress token | `progressToken` | client request `_meta` (unprefixed) | no |
| subscription id | `io.modelcontextprotocol/subscriptionId` | server-side: `_meta` of notifications delivered on a `subscriptions/listen` stream ("the server MUST include") | n/a for mocapi (subscriptions not implemented) |
| trace parent | `traceparent` | any `_meta` (unprefixed — explicit exception to the prefix rule) | no — W3C Trace Context format |
| trace state | `tracestate` | any `_meta` (unprefixed exception) | no — W3C Trace Context format |
| baggage | `baggage` | any `_meta` (unprefixed exception) | no — W3C Baggage format |

Notes: the trace-context keys are defined in spec **prose only**
(`basic/index#meta`, "OpenTelemetry trace context"), not in schema.ts —
plan Tasks 1.3/6.1's assumed names are confirmed correct, including the
unprefixed form. `io.modelcontextprotocol/subscriptionId` exists but is
only relevant to `subscriptions/listen` streams; include the constant in
`McpMetaKeys` for completeness or omit with a comment — implementer's call.

On HTTP, `_meta` `protocolVersion` MUST match the `MCP-Protocol-Version`
header, else `400 Bad Request` (RequestMetaObject JSDoc).

---

## 6. Method-name inventory (post-migration `McpMethods`)

Every method literal in the draft schema, with mocapi's stance:

| Method | Draft status | mocapi |
|---|---|---|
| `server/discover` | new (servers MUST implement) | **implement** (Task 2.3) — ADD constant |
| `tools/list` | unchanged | implement |
| `tools/call` | unchanged | implement |
| `prompts/list` | unchanged | implement |
| `prompts/get` | unchanged | implement |
| `resources/list` | unchanged | implement |
| `resources/templates/list` | unchanged | implement |
| `resources/read` | unchanged | implement |
| `completion/complete` | unchanged | implement |
| `subscriptions/listen` | new | **not implemented** (ADR-0022) → `-32601`; keep constant for model completeness |
| `sampling/createMessage` | deprecated; embedded `InputRequest` method string only (no longer a JSON-RPC request type) | not implemented; constant kept `@Deprecated` (referenced by the `InputRequest` union if D1 accepted) |
| `roots/list` | deprecated; embedded `InputRequest` method string only | not implemented; constant kept `@Deprecated` (same condition) |
| `elicitation/create` | embedded `InputRequest` method string only | **emitted** inside `InputRequiredResult.inputRequests` (MRTR) — keep constant |
| `notifications/cancelled` | unchanged (`requestId` now optional) | receive; partial processing (ADR-0022) |
| `notifications/progress` | unchanged (not deprecated) | **send** — keep |
| `notifications/message` | **deprecated** (SEP-2577) | not implemented; constant kept `@Deprecated` |
| `notifications/resources/list_changed` | unchanged (now opt-in via subscriptions) | not implemented (static discovery) |
| `notifications/resources/updated` | unchanged (now opt-in via `resourceSubscriptions`) | not implemented |
| `notifications/tools/list_changed` | unchanged | not implemented |
| `notifications/prompts/list_changed` | unchanged | not implemented |
| `notifications/elicitation/complete` | unchanged (URL-mode elicitation) | not implemented (URL mode declined, ADR-0022) |
| `notifications/subscriptions/acknowledged` | new | not implemented (ADR-0022) |

**Constants to DELETE** (method gone from spec): `INITIALIZE` (`initialize`),
`PING` (`ping`), `RESOURCES_SUBSCRIBE`, `RESOURCES_UNSUBSCRIBE`,
`LOGGING_SET_LEVEL` (`logging/setLevel`), `NOTIFICATIONS_INITIALIZED`,
`NOTIFICATIONS_ROOTS_LIST_CHANGED`.

**Constants to ADD:** `SERVER_DISCOVER` (`server/discover`),
`SUBSCRIPTIONS_LISTEN` (`subscriptions/listen`),
`NOTIFICATIONS_SUBSCRIPTIONS_ACKNOWLEDGED` (`notifications/subscriptions/acknowledged`).

---

## 7. Surprises / contradictions with the plan

- **S1 — `server/discover` REQUIRES the full `_meta` envelope.** Plan Task
  2.3 says "Must work with **no** `_meta` envelope (it's the
  version-selection probe)". Wrong per the schema and the discover page (see
  §4.1). The bootstrap works via `UnsupportedProtocolVersionError` carrying
  `data.supported`, not via an envelope-less probe. Task 2.3's acceptance
  criteria need rewording.
- **S2 — required `resultType` on every result.** Not mentioned anywhere in
  the plan's Task 1.5 modify-list, yet it touches all 9 surviving result
  classes plus both new ones. `"complete"` everywhere except
  `InputRequiredResult` (`"input_required"`).
- **S3 — six cacheable results, not five.** `DiscoverResult extends
  CacheableResult` — `ttlMs`/`cacheScope` are required on the discover
  response too. Plan Tasks 1.5/5.1 say "five"; Task 2.3's handler must also
  populate cache fields.
- **S4 — `MissingRequiredClientCapabilityError` (`-32003`) is a new spec
  error the plan never mentions.** Servers MUST return it (HTTP 400) when a
  request needs an undeclared client capability — this replaces mocapi's
  current `McpElicitationNotSupportedException`-style handling at the wire
  level (Task 4.2's capability check should map to `-32003`, not a generic
  error).
- **S5 — `ping` is fully removed.** Expected by the plan ("ping types") but
  worth pinning: no `ping` literal exists anywhere in the draft schema.
- **S6 — `ClientCapabilities.roots` lost `listChanged`** (now empty `{}`),
  and `notifications/roots/list_changed` is gone — slightly more than
  "deprecate Roots".
- **S7 — `ToolChoice` shape mismatch** (pre-existing): mocapi's
  `Auto`/`None`/`Specific` sealed model doesn't match the spec's
  `{ mode?: "auto" | "required" | "none" }` — there is no named-tool form in
  the draft, and `"required"` is missing from mocapi. Align while deprecating
  (§2).
- **S8 — sampling/roots request types are no longer JSON-RPC requests.**
  `CreateMessageRequest`, `ListRootsRequest`, `ElicitRequest` exist only as
  embedded `InputRequest` envelopes (no `jsonrpc`/`id`). There is no
  server→client RPC channel left in the protocol — consistent with the
  plan's stateless stance.
- **S9 — `CancelledNotificationParams.requestId` became optional** (it was
  required in 2025-11-25). No Java change; server-side validation must not
  require it.
- **S10 — missing-envelope error code is `-32602`**, HTTP 400 (spec
  `basic/index#meta`) — confirms Task 2.1's "malformed → invalid params";
  note that an *unsupported version inside a well-formed envelope* is
  `-32004`, and the two cases must not be conflated.
- **S11 — D1 (above):** strict 1:1 modeling of the MRTR unions drags in
  seven never-modeled deprecated types. Needs a call before Task 1.4.

## 8. Pre-existing fidelity gaps (carried over from 2025-11-25 — NOT revision deltas)

For the record, mocapi's model already simplified these 2025-11-25 shapes;
the draft does not change the situation. No action required by this
migration unless Task 1.4/1.5 wants to close them opportunistically:

- `_meta?: MetaObject` members on `Tool`, `Prompt`, `Resource`,
  `ResourceTemplate`, content blocks, `ResourceContents`, `EmbeddedResource`
  are not modeled.
- `ToolAnnotations` (and `Tool.annotations`) not modeled; `Tool`/`Resource`/
  `ResourceTemplate`/`Implementation` `icons` not modeled (only `Prompt` has
  icons); `Resource.annotations`/`size`/`title`, `ResourceTemplate.title`,
  `PromptArgument.title`, `ResourceLink`'s full `Resource` field set,
  `Implementation.websiteUrl` not modeled.
- `SamplingCapability` empty record vs `{context?, tools?}` (now moot —
  deprecated).

## 9. UNCHANGED (52 classes)

Annotations, AudioContent, BlobResourceContents, BooleanSchema,
CompleteRequestParams (field set unchanged; its `_meta` type updates via
RequestMeta), Completion, CompletionArgument, CompletionContext,
CompletionRef, CompletionsCapability, ContentBlock, ElicitAction,
ElicitationCompleteNotificationParams, ElicitResult, EmbeddedResource,
EnumItemsSchema, EnumOption, EnumSchema, Icon, ImageContent, JsonRpcError,
LegacyTitledEnumSchema, MultiSelectEnumSchema, NotificationParams,
PrimitiveSchemaDefinition, ProgressNotification, ProgressNotificationParams,
Prompt, PromptArgument, PromptMessage, PromptReference, PromptsCapability,
Resource, ResourceContents, ResourceLink, ResourcesCapability,
ResourceTemplate, ResourceTemplateReference,
ResourceUpdatedNotificationParams, Role, SingleSelectEnumSchema,
StringFormat, StringSchema, TextContent, TextResourceContents,
TitledEnumItemsSchema, TitledMultiSelectEnumSchema,
TitledSingleSelectEnumSchema, Tool, ToolsCapability,
UntitledMultiSelectEnumSchema, UntitledSingleSelectEnumSchema.

(`Tool` note: the draft now spells out that `inputSchema` defaults to JSON
Schema 2020-12 and admits any 2020-12 keyword beside the mandatory root
`type: "object"`, and `outputSchema` is any 2020-12 schema — mocapi's
`ObjectNode` representation already accommodates this; the behavioral work
is Phase 5, Task 5.3.)

---

## 2026-07-15 re-diff against upstream `main`

The RC snapshot above was pinned on 2026-06-11 from commit `77cb2648`.
Upstream `schema/draft/schema.ts` has since moved. Re-pulled on 2026-07-15
from `https://raw.githubusercontent.com/modelcontextprotocol/modelcontextprotocol/main/schema/draft/schema.{ts,json}`.

- **Last commit touching `schema/draft/schema.ts` at re-fetch time:**
  `9a4ff8af92ba00cbddbf94672dfade9279987e66`. (`LATEST_PROTOCOL_VERSION`
  remains `"2026-07-28"`.)
- **Refreshed snapshots committed alongside this doc:**
  `docs/plans/2026-07-28-schema.ts`, `docs/plans/2026-07-28-schema.json`.

### Semantic deltas that touched mocapi's implemented surface (fixed)

1. **Error codes renumbered** (upstream `f505a6c7` + `73ab7d2f`). The
   implementation-defined server-error range is now partitioned: `-32000`
   to `-32019` stays implementation-defined (grandfathered SDK usage) and
   `-32020` to `-32099` is reserved for spec-defined errors. New values:

   | Error | Old (RC) | New |
   | --- | --- | --- |
   | `HeaderMismatch` | `-32001` | `-32020` |
   | `MissingRequiredClientCapabilityError` | `-32003` | `-32021` |
   | `UnsupportedProtocolVersionError` | `-32004` | `-32022` |

   Updated `MissingRequiredClientCapabilityErrorData.CODE`,
   `UnsupportedProtocolVersionErrorData.CODE`,
   `McpHeaderValidator.HEADER_MISMATCH_CODE`, and every javadoc/comment/test
   citing the old numbers (semantic mapping, by meaning). mocapi's guard
   denial (`JsonRpcErrorCodes.FORBIDDEN = -32010`) sits in the
   implementation-defined sub-range and is unchanged (ADR-0023).

2. **`ElicitationCompleteNotification` /
   `ElicitationCompleteNotificationParams` removed** from the spec. Deleted
   `ElicitationCompleteNotificationParams.java` and its serialization test.
   `ClientNotification` also dropped `ProgressNotification`, and
   `ServerNotification` dropped `ElicitationCompleteNotification`.

3. **URL-mode elicitation `elicitationId` removed.** `ElicitRequestURLParams`
   is now `{ mode, message, url }`; the Java record is now
   `(String message, String url)`. Test assertions updated.

4. **`SubscriptionsListenResult` added.** New result type
   (`extends Result`) with a required `resultType` and a required `_meta`
   (`SubscriptionsListenResultMeta`) carrying the required
   `io.modelcontextprotocol/subscriptionId` (a `RequestId`). Added
   `SubscriptionsListenResult` + `SubscriptionsListenResultMeta` records and
   a round-trip test. mocapi does not implement subscriptions (ADR-0022);
   these exist for 1:1 model fidelity, matching
   `SubscriptionsListenRequestParams`.

5. **`CancelledNotificationParams.requestId` reverted to REQUIRED.** No code
   change: mocapi already models it as a non-optional `Object` component and
   the existing round-trips exercise both string and numeric IDs. Prose also
   narrowed cancellation to client-initiated, with a stdio-only carve-out for
   the server to terminate a `subscriptions/listen` stream.

### Additional upstream deltas observed (prose/semantic only — NOT fixed)

These do not alter mocapi's implemented wire surface and were left as-is:

- **`HeaderMismatch` now appears in `schema.ts`** as a `HEADER_MISMATCH`
  const (`-32020`) plus a `HeaderMismatchError` interface. Previously it was
  transport-prose only. mocapi still sources the constant transport-side in
  `McpHeaderValidator` (not `mocapi-model`); relocating it would be an
  architecturally significant change requiring its own ADR. Flagged, not moved.
- **`NotificationMetaObject`** — `NotificationParams._meta` is now typed as
  `NotificationMetaObject` (adds an optional `io.modelcontextprotocol/subscriptionId`).
  mocapi models notification `_meta` as an untyped `ObjectNode`, so no change.
- **List-changed notification prose** (`resources`/`prompts`/`tools`
  `list_changed`) now says delivery happens only on a `subscriptions/listen`
  stream when requested via the matching `*ListChanged` filter field.
  Subscriptions are not implemented (ADR-0022); no code change.
- **`SubscriptionsAcknowledgedNotification`** prose tightened
  (first-message ordering per subscription ID). Not implemented; no change.
- **`CacheableResult.cacheScope`** prose redefined `public`/`private` in
  terms of authorization context rather than user; the field shape is
  unchanged, so no code change.
- **`Tool.inputSchema`** prose adds an optional `x-mcp-header` property-schema
  annotation (mirror an argument into an HTTP header on Streamable HTTP).
  mocapi carries `inputSchema` as an opaque `ObjectNode`, so no structural
  change; this is a potential future feature, not a model gap.
- **`ListRootsRequest.params`** narrowed from `RequestParams` to
  `{ _meta?: MetaObject }`. `roots/list` is a server→client input request
  mocapi never issues (`ListRootsResult` exists for union fidelity only); no
  change.

## 2026-07-17 re-diff #2 — upstream 71e30695 (PR #3002)

Re-pulled `docs/plans/2026-07-28-schema.ts` and `docs/plans/2026-07-28-schema.json`
from upstream `main`, re-pinning past the prior 2026-07-15 pin.

- **New pinned SHA:** `71e306956a4959c9655e5036be215d41986596e6` (PR #3002),
  superseding the previous pin `9a4ff8af92ba00cbddbf94672dfade9279987e66`.
  `LATEST_PROTOCOL_VERSION` remains `"2026-07-28"`.

### Semantic deltas versus the prior pin (9a4ff8af)

1. **`io.modelcontextprotocol/clientInfo` demoted from REQUIRED to OPTIONAL**
   in `RequestMetaObject`. The required set on every client request `_meta`
   is now just `io.modelcontextprotocol/protocolVersion` +
   `io.modelcontextprotocol/clientCapabilities`. → **mocapi:**
   `MetaEnvelopeParser` already parses `clientInfo` only when present (still
   `-32602` if present-but-malformed); downstream consumers were already
   null-safe. No code change required.
2. **NEW `io.modelcontextprotocol/serverInfo` (an `Implementation`) added to
   `ResultMetaObject`**; servers SHOULD include it on every response. →
   **mocapi:** `DefaultMcpServer` injects it into every successful result's
   `_meta` (merge, never clobber an existing `_meta` entry), default-on,
   opt-out via the `mocapi.emit-server-info` property (default `true`).
3. **Top-level `serverInfo` field REMOVED from `DiscoverResult`** (now
   delivered only via `_meta`, per delta 2 above). → **mocapi:** removed the
   `DiscoverResult.serverInfo` record component; discover clients now receive
   `serverInfo` via the `_meta` injection from delta 2. Keeps the model 1:1
   with the schema (constitution I7).

No other semantic deltas affecting mocapi's implemented surface were found
in this re-diff.

## 2026-07-28 finalization — spec RELEASED (Plan Task 9.3)

The `2026-07-28` revision was **finalized** today: upstream `main` promoted it
out of `schema/draft/` into a dedicated `schema/2026-07-28/` directory
(landing commit "Add 2026-07-28 MCP specification", 2026-07-28 15:56 UTC,
plus a follow-up fix at 16:42 UTC). PR #3002 is merged and folded in. The spec
is no longer draft. This supersedes the July-20 monitor note (PR #7), which
recorded the spec as still draft.

Re-pinned `docs/plans/2026-07-28-schema.{ts,json}` from the prior draft pin
(`71e30695`) to the **finalized** `schema/2026-07-28/` snapshot. Both files are
now byte-identical to upstream `schema/2026-07-28/`.

### Semantic deltas versus the prior pin (71e30695)

1. **Doc-link retargeting.** Every `@see [.../_meta](/specification/draft/...)`
   reference in `schema.ts` changed `draft` → `2026-07-28`. Snapshot-comment
   only; no structural or wire impact. Absorbed by the re-pin.
2. **`SubscriptionsListenResultMeta` renamed to
   `SubscriptionsListenResultMetaObject`** (the `$defs` key + its `$ref`). The
   `_meta` object's property set is unchanged — pure type-name rename. →
   **mocapi:** renamed the mirroring model record
   `SubscriptionsListenResultMeta` → `SubscriptionsListenResultMetaObject`
   (record + `SubscriptionsListenResult.meta` component + serialization test),
   preserving the "spec's `{@code …}`" javadoc alignment. The round-trip test
   confirms byte-identical wire output
   (`"_meta":{"io.modelcontextprotocol/subscriptionId":"sub-1"}`). Subscriptions
   remain unimplemented (ADR-0022); this type exists only for 1:1 model fidelity.
3. **NEW `SubscriptionsListenResultResponse`** — a JSON-RPC result-envelope
   wrapper (`{ id, jsonrpc, result }`) around `SubscriptionsListenResult`. →
   **mocapi:** no change. mocapi models `*Result` payloads only and has never
   modeled per-method JSON-RPC `*Response` envelope types (none exist for any
   method); envelopes are wrapped generically at the transport layer. Adding one
   here would break that invariant, so it is deliberately omitted.

No other deltas. No ADR required: this hits none of the ADR triggers (no
SPI/transport/capability/not-implemented-list change, no new behavioral seam) —
a spec-alignment rename with identical wire output, in the same class as the
prior re-pin commits.

## 2026-08-02 monitor check — no drift, finalized pin still current

Daily MCP `2026-07-28` monitor run. **No convergence work needed.**

**RELEASE status:** confirmed still finalized (unchanged since the
2026-07-28 finalization above). `schema/` on upstream `main` contains
`2024-11-05`, `2025-03-26`, `2025-06-18`, `2025-11-25`, `2026-07-28`, and
`draft` — no newer date-stamped directory has appeared.

**`schema/2026-07-28/schema.ts` commit history:** exactly the two commits
already accounted for in the finalization re-pin above — `b488c16` ("Add
2026-07-28 MCP specification") and `271ecc9` ("fix(schema): apply
subscriptions/listen envelope and MetaObject rename to 2026-07-28"). No
commits have landed on the finalized directory since. The pinned
`docs/plans/2026-07-28-schema.ts` already contains both
`SubscriptionsListenResultMetaObject` (line 1326) and
`SubscriptionsListenResultResponse` (line 1362) from the second commit —
spot-checked directly against the committed snapshot today.

**`schema/draft/schema.ts` commit history:** one new commit since the prior
baseline (`71e3069`, 2026-07-16) — `f7e99af` ("schema(draft): align
subscriptions/listen with envelope and `_meta` naming conventions",
2026-07-28). Fetched its patch: it applies the *identical* two changes
already documented above (`SubscriptionsListenResultMeta` →
`SubscriptionsListenResultMetaObject` rename, plus the
`SubscriptionsListenResultResponse` envelope addition) to `schema/draft/`,
evidently to keep `draft/` in sync with the now-finalized `2026-07-28/`
content rather than starting a new revision. `draft/schema.ts` still
reports `LATEST_PROTOCOL_VERSION = "2026-07-28"`. → **mocapi:** no action.
This is a backport of a change mocapi already converged (delta 2 already
applied; delta 3, the `*ResultResponse` envelope, already deliberately
declined per the reasoning above, which still holds). No new draft
revision has begun.

**PR #7 disposition:** closed as superseded. It was opened 2026-07-20
against the pre-finalization draft state and its branch predates the
finalization commits that landed directly on `mcp-2026-07-28`
(`07d24db`); it showed `mergeable_state: dirty` and its "still draft, not
finalized" conclusion is now stale. This addendum supersedes it.

**Snapshot status:** not re-pinned — not needed. The existing pin
(byte-identical to `schema/2026-07-28/` per the prior re-pin) is still
current; verified no new commits exist to re-pin against.

**`mvn verify`:** not run. This is a docs-only change (no
`mocapi-model`/`mocapi-server`/transport code touched), so the build is
unaffected. Note for future runs: this sandbox still only has JDK 21
(`mocapi-parent` enforces `[25,)`), same pre-existing environment gap
noted in the 2026-07-20 addendum — unrelated to this change.

**Confidence:** high. Three independent checks agree there is no
unconverged drift: (1) the finalized directory's commit list is unchanged
and both its commits are already reflected in the pinned snapshot and
model code, (2) the one new draft commit is a content-identical backport
of already-converged work, (3) the `schema/` directory listing shows no
new revision has started. No open uncertainties beyond the
already-tracked `HeaderMismatch` location note (unchanged, not touched by
this run).

## 2026-08-03 monitor check — no drift, finalized pin still current

Daily MCP `2026-07-28` monitor run. **No convergence work needed.**

**RELEASE status:** confirmed still finalized (unchanged since the
2026-07-28 finalization). `schema/` on upstream `main` contains
`2024-11-05`, `2025-03-26`, `2025-06-18`, `2025-11-25`, `2026-07-28`, and
`draft` — no newer date-stamped directory has appeared.

**`schema/draft/schema.ts` commit history:** newest commit is still
`f7e99af` (Jul 28, 2026, "schema(draft): align subscriptions/listen with
envelope and `_meta` naming conventions") — the same commit already
triaged in the 2026-08-02 addendum above as a content-identical backport
of already-converged work. No commits have landed since. `draft/schema.ts`
still reports `LATEST_PROTOCOL_VERSION = "2026-07-28"`. → **mocapi:** no
action.

**`schema/2026-07-28/schema.ts` (finalized) commit history:** not
independently re-walked this run — the 2026-08-02 addendum already
confirmed it is unchanged and byte-identical to the pinned snapshot, and
today's directory listing and draft-history checks show no upstream
activity since then that would touch it.

**Note on tooling:** `api.github.com` returned HTTP 403 via WebFetch this
run (both the commits endpoint and the contents endpoint); fell back to
the `github.com` HTML commit-log and tree views for the same data, which
succeeded and cross-checks consistently with the 2026-08-02 findings.
Worth a human look if this persists on future runs.

**Snapshot status:** not re-pinned — not needed. No new commits exist to
re-pin against since the 2026-08-02 check.

**`mvn verify`:** not run. Docs-only change, no `mocapi-model`/
`mocapi-server`/transport code touched.

**Confidence:** high on "no new drift since 2026-08-02" — the draft
commit history and the `schema/` directory listing were both independently
re-checked today and match the prior run exactly. Slightly lower
confidence (medium) on the finalized directory specifically, since it
was not independently re-walked this run (see above) — carried forward
from the 2026-08-02 high-confidence check rather than re-verified byte
for byte today.
