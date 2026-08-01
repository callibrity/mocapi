export const PROTOCOL_VERSION = "2026-07-28";

const APP_MIME = "text/html;profile=mcp-app";

export function buildHeaders(method: string, mcpName?: string): Record<string, string> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "Accept": "application/json, text/event-stream",
    "MCP-Protocol-Version": PROTOCOL_VERSION,
    "Mcp-Method": method,
  };
  if (mcpName) headers["Mcp-Name"] = mcpName;
  return headers;
}

export function buildMeta(): Record<string, unknown> {
  return {
    "io.modelcontextprotocol/protocolVersion": PROTOCOL_VERSION,
    "io.modelcontextprotocol/clientInfo": { name: "mocapi-dev-host", version: "0.0.0" },
    "io.modelcontextprotocol/clientCapabilities": {
      extensions: { "io.modelcontextprotocol/ui": { mimeTypes: [APP_MIME] } },
    },
  };
}
