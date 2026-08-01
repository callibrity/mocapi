import { AppBridge, PostMessageTransport } from "@modelcontextprotocol/ext-apps/app-bridge";
import { CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
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

  // 1. Point the iframe at the sandbox proxy (origin B). The sandbox reads the
  //    bundle via the proxy-ready handshake below.
  iframe.src = `${sandboxOrigin}/sandbox.html`;
  await new Promise<void>((resolve) => iframe.addEventListener("load", () => resolve(), { once: true }));

  // 2. Host-side bridge in NO-CLIENT mode: we proxy server calls ourselves,
  //    because an SDK Client would try to `initialize` against mocapi (2026-07-28
  //    has no initialize) and fail.
  const bridge = new AppBridge(
    null,
    { name: "mocapi-dev-host", version: "0.0.0" },
    { /* McpUiHostCapabilities — none of the optional host features in v1 */ },
  );

  // 3. Proxy the app's server tool calls to our 2026-07-28 client.
  bridge.setRequestHandler(CallToolRequestSchema, async (req) =>
    client.callTool(req.params.name, (req.params.arguments ?? {}) as Record<string, unknown>),
  );

  const transport = new PostMessageTransport(iframe.contentWindow!, iframe.contentWindow!);
  await bridge.connect(transport);

  // 4. Hand the sandbox the bundle HTML + deliver the initiating tool result.
  //    Params confirmed against McpUi*Notification["params"] in the SDK's
  //    spec.types.d.ts and mirrored from basic-host/src/implementation.ts:
  //    - sandbox-resource-ready takes `{ html, csp?, permissions? }` (no `resource`
  //      wrapper — `bundleUri` is not part of this notification's payload).
  //    - tool-input takes `{ arguments? }` (no `toolName` field).
  //    - tool-result's params type is `CallToolResult` itself, not a wrapper.
  await bridge.sendSandboxResourceReady({ html: bundleHtml });
  await bridge.sendToolInput({ arguments: toolArgs });
  await bridge.sendToolResult(toolResult);

  return { dispose: () => bridge.close() };
}
