import { AppBridge, PostMessageTransport } from "@modelcontextprotocol/ext-apps/app-bridge";
import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import type { DevHostMcpClient } from "../mcp/transport";

export interface MountOptions {
  iframe: HTMLIFrameElement;
  sandboxOrigin: string;
  bundleUri: string;
  bundleHtml: string;
  toolName: string;
  toolArgs: Record<string, unknown>;
  toolResult: CallToolResult;
  client: DevHostMcpClient;
}

export async function mountApp(opts: MountOptions): Promise<{ dispose(): void }> {
  const { iframe, sandboxOrigin, bundleHtml, toolArgs, toolResult, client } = opts;

  // 1. Host-side bridge in NO-CLIENT mode: we proxy server calls ourselves,
  //    because an SDK Client would try to `initialize` against mocapi (2026-07-28
  //    has no initialize) and fail. `serverTools: {}` is the capability we
  //    actually implement — the host proxies server tool calls to the app.
  const bridge = new AppBridge(
    null,
    { name: "mocapi-dev-host", version: "0.0.0" },
    { serverTools: {} },
  );

  // 2. Register readiness handlers and the no-client tool-call handler BEFORE
  //    navigating the iframe, so an early `sandbox-proxy-ready` notification
  //    (fired as soon as the sandbox's own script runs) is never missed.
  //    Mirrors basic-host/src/implementation.ts, which sets up its
  //    sandbox-proxy-ready listener before assigning `iframe.src`, and sets
  //    `oninitialized` before `connect()`.
  const sandboxReady = new Promise<void>((resolve) => {
    bridge.onsandboxready = () => resolve();
  });
  const appInitialized = new Promise<void>((resolve) => {
    bridge.oninitialized = () => resolve();
  });

  // 3. Proxy the app's server tool calls to our 2026-07-28 client. `oncalltool`
  //    is the documented no-client setter (keeps the SDK's premature-call
  //    diagnostics), matching the constructor's "Without MCP client" example.
  bridge.oncalltool = async (params) => client.callTool(params.name, (params.arguments ?? {}) as Record<string, unknown>);

  // 4. Point the iframe at the sandbox proxy (origin B), then connect the
  //    bridge over it.
  iframe.src = `${sandboxOrigin}/sandbox.html`;
  const transport = new PostMessageTransport(iframe.contentWindow!, iframe.contentWindow!);
  await bridge.connect(transport);

  // 5. Wait for the sandbox proxy's readiness notification before handing it
  //    the bundle HTML — per `onsandboxready`'s doc: "When this fires, the
  //    host should call sendSandboxResourceReady". Params confirmed against
  //    McpUiSandboxResourceReadyNotification["params"] in spec.types.d.ts and
  //    mirrored from implementation.ts: `{ html, csp?, permissions? }` (no
  //    `resource` wrapper — `bundleUri` is not part of this notification).
  await sandboxReady;
  await bridge.sendSandboxResourceReady({ html: bundleHtml });

  // 6. Wait for the View's initialization handshake before sending tool
  //    input/result — per `connect()`'s doc: "wait for the `oninitialized`
  //    callback before sending tool input and other data to the View".
  //    tool-input takes `{ arguments? }` (no `toolName` field); tool-result's
  //    params type is `CallToolResult` itself, not a wrapper.
  await appInitialized;
  await bridge.sendToolInput({ arguments: toolArgs });
  await bridge.sendToolResult(toolResult);

  return { dispose: () => bridge.close() };
}
