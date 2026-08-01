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

/** One MCP exchange on the wire, surfaced to the inspector. */
export interface Exchange {
  id: number;
  method: string;
  mcpName?: string;
  request: unknown;
  status: "pending" | "ok" | "error";
  response?: unknown;
  error?: { code: number; message: string };
  ms?: number;
  at: number;
}

export type ExchangeListener = (ex: Exchange) => void;

let rpcId = 1;
let exchangeSeq = 0;

async function rpc<T>(
  endpoint: string,
  method: string,
  params: Record<string, unknown>,
  mcpName: string | undefined,
  onExchange: ExchangeListener | undefined,
): Promise<T> {
  const id = ++exchangeSeq;
  const at = Date.now();
  const started = performance.now();
  const request = {
    jsonrpc: "2.0",
    id: rpcId++,
    method,
    params: { ...params, _meta: buildMeta() },
  };
  const base = { id, method, mcpName, request, at };
  onExchange?.({ ...base, status: "pending" });
  const done = (patch: Partial<Exchange>) =>
    onExchange?.({ ...base, ms: Math.round(performance.now() - started), status: "ok", ...patch });

  let res: Response;
  try {
    res = await fetch(endpoint, {
      method: "POST",
      headers: buildHeaders(method, mcpName),
      body: JSON.stringify(request),
    });
  } catch (e) {
    done({ status: "error", error: { code: 0, message: `Network error: ${String(e)}` } });
    throw new McpError(0, `Network error contacting ${endpoint}: ${String(e)}`);
  }

  let json: any;
  try {
    json = await res.json();
  } catch {
    // Non-JSON response.
    const message = res.ok
      ? `Invalid JSON response from ${endpoint}`
      : `HTTP ${res.status} from ${endpoint}`;
    done({ status: "error", error: { code: res.status, message } });
    throw new McpError(res.status, message);
  }

  // JSON-RPC error takes precedence over HTTP status (mocapi maps error codes to HTTP status).
  if (json.error) {
    done({ status: "error", response: json, error: json.error });
    throw new McpError(json.error.code, json.error.message);
  }

  // No result and an HTTP failure -> surface the HTTP error.
  if (!("result" in json) && !res.ok) {
    done({ status: "error", response: json, error: { code: res.status, message: `HTTP ${res.status} from ${endpoint}` } });
    throw new McpError(res.status, `HTTP ${res.status} from ${endpoint}`);
  }

  done({ status: "ok", response: json.result });
  return json.result as T;
}

export class DevHostMcpClient {
  constructor(
    private readonly endpoint: string,
    private readonly onExchange?: ExchangeListener,
  ) {}

  discover(): Promise<DiscoverResult> {
    return rpc<DiscoverResult>(this.endpoint, "server/discover", {}, undefined, this.onExchange);
  }
  async listTools(): Promise<Tool[]> {
    const { tools } = await rpc<{ tools: Tool[] }>(this.endpoint, "tools/list", {}, undefined, this.onExchange);
    return tools;
  }
  callTool(name: string, args: Record<string, unknown>): Promise<CallToolResult> {
    return rpc<CallToolResult>(this.endpoint, "tools/call", { name, arguments: args }, name, this.onExchange);
  }
  readResource(uri: string): Promise<ReadResourceResult> {
    return rpc<ReadResourceResult>(this.endpoint, "resources/read", { uri }, uri, this.onExchange);
  }
}
