import type { CallToolResult, ReadResourceResult, Tool } from "@modelcontextprotocol/sdk/types.js";
import { buildHeaders, buildMeta } from "./envelope";

export interface DiscoverResult {
  supportedVersions: string[];
  capabilities: { extensions?: Record<string, unknown> };
}

export class McpError extends Error {
  constructor(public readonly code: number, message: string) {
    super(message);
    this.name = "McpError";
  }
}

let nextId = 1;

async function rpc<T>(endpoint: string, method: string, params: Record<string, unknown>, mcpName?: string): Promise<T> {
  const res = await fetch(endpoint, {
    method: "POST",
    headers: buildHeaders(method, mcpName),
    body: JSON.stringify({ jsonrpc: "2.0", id: nextId++, method, params: { ...params, _meta: buildMeta() } }),
  });

  let json: any;
  try {
    json = await res.json();
  } catch {
    // Non-JSON response on error
    if (!res.ok) throw new McpError(res.status, `HTTP ${res.status} from ${endpoint}`);
    throw new McpError(res.status, `Invalid JSON response from ${endpoint}`);
  }

  // JSON-RPC error takes precedence over HTTP status
  if (json.error) throw new McpError(json.error.code, json.error.message);

  // If no result and HTTP error, throw HTTP error
  if (!("result" in json) && !res.ok) {
    throw new McpError(res.status, `HTTP ${res.status} from ${endpoint}`);
  }

  return json.result as T;
}

export class DevHostMcpClient {
  constructor(private readonly endpoint: string) {}

  discover(): Promise<DiscoverResult> {
    return rpc<DiscoverResult>(this.endpoint, "server/discover", {});
  }
  async listTools(): Promise<Tool[]> {
    const { tools } = await rpc<{ tools: Tool[] }>(this.endpoint, "tools/list", {});
    return tools;
  }
  callTool(name: string, args: Record<string, unknown>): Promise<CallToolResult> {
    return rpc<CallToolResult>(this.endpoint, "tools/call", { name, arguments: args }, name);
  }
  readResource(uri: string): Promise<ReadResourceResult> {
    return rpc<ReadResourceResult>(this.endpoint, "resources/read", { uri }, uri);
  }
}
