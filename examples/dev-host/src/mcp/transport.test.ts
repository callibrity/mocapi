import { describe, expect, it, vi } from "vitest";
import { buildHeaders, buildMeta, PROTOCOL_VERSION } from "./envelope";
import { DevHostMcpClient, McpError } from "./transport";

describe("envelope", () => {
  it("sets the 2026-07-28 routing headers, with Mcp-Name only when given", () => {
    expect(buildHeaders("tools/list")).toMatchObject({
      "Content-Type": "application/json",
      "Accept": "application/json, text/event-stream",
      "MCP-Protocol-Version": PROTOCOL_VERSION,
      "Mcp-Method": "tools/list",
    });
    expect(buildHeaders("tools/list")["Mcp-Name"]).toBeUndefined();
    expect(buildHeaders("tools/call", "get-time")["Mcp-Name"]).toBe("get-time");
  });

  it("declares protocolVersion + the ui extension in clientCapabilities", () => {
    const meta = buildMeta() as Record<string, any>;
    expect(meta["io.modelcontextprotocol/protocolVersion"]).toBe(PROTOCOL_VERSION);
    expect(
      meta["io.modelcontextprotocol/clientCapabilities"].extensions["io.modelcontextprotocol/ui"],
    ).toBeTruthy();
  });
});

describe("DevHostMcpClient", () => {
  function mockFetch(body: unknown) {
    return vi.fn().mockResolvedValue(
      new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } }),
    );
  }

  it("posts a well-formed request and returns result.tools", async () => {
    const fetchMock = mockFetch({ jsonrpc: "2.0", id: 1, result: { tools: [{ name: "get-time" }] } });
    vi.stubGlobal("fetch", fetchMock);
    const tools = await new DevHostMcpClient("http://x/mcp").listTools();
    expect(tools).toEqual([{ name: "get-time" }]);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://x/mcp");
    expect((init.headers as Record<string, string>)["Mcp-Method"]).toBe("tools/list");
    expect(JSON.parse(init.body).method).toBe("tools/list");
    vi.unstubAllGlobals();
  });

  it("throws McpError on a JSON-RPC error response", async () => {
    vi.stubGlobal("fetch", mockFetch({ jsonrpc: "2.0", id: 1, error: { code: -32602, message: "Missing required _meta key" } }));
    await expect(new DevHostMcpClient("http://x/mcp").listTools()).rejects.toMatchObject({
      code: -32602,
    });
    vi.unstubAllGlobals();
  });

  it("callTool/readResource pass Mcp-Name", async () => {
    const fetchMock = mockFetch({ jsonrpc: "2.0", id: 1, result: { content: [] } });
    vi.stubGlobal("fetch", fetchMock);
    await new DevHostMcpClient("http://x/mcp").callTool("get-time", {});
    expect((fetchMock.mock.calls[0][1].headers as Record<string, string>)["Mcp-Name"]).toBe("get-time");
    vi.unstubAllGlobals();
  });
});
