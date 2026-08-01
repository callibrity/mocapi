import { useCallback, useRef, useState } from "react";
import { getToolUiResourceUri } from "@modelcontextprotocol/ext-apps/app-bridge";
import type { Tool } from "@modelcontextprotocol/sdk/types.js";
import { DevHostMcpClient, McpError } from "../mcp/transport";
import { mountApp } from "../host/bridge";
import { ToolCard } from "./ToolCard";

const UI_CAP = "io.modelcontextprotocol/ui";

export function App() {
  const [url, setUrl] = useState("http://localhost:8888/mcp");
  const [error, setError] = useState<string | null>(null);
  const [uiTools, setUiTools] = useState<{ tool: Tool; uiUri: string }[]>([]);
  const clientRef = useRef<DevHostMcpClient | null>(null);
  const [sandboxOrigin, setSandboxOrigin] = useState("http://localhost:5174");
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const [active, setActive] = useState<string | null>(null);

  const connect = useCallback(async () => {
    setError(null);
    setUiTools([]);
    setActive(null);
    try {
      // sandbox origin is provided by serve.ts
      setSandboxOrigin((await (await fetch("/api/sandbox-origin")).json()).origin);
      const client = new DevHostMcpClient(url);
      clientRef.current = client;
      const discover = await client.discover();
      if (!discover.capabilities.extensions?.[UI_CAP]) {
        setError("This server does not advertise the MCP Apps (io.modelcontextprotocol/ui) capability.");
        return;
      }
      const tools = await client.listTools();
      const withUi = tools
        .map((tool) => ({ tool, uiUri: getToolUiResourceUri(tool) }))
        .filter((t): t is { tool: Tool; uiUri: string } => Boolean(t.uiUri));
      if (withUi.length === 0) setError("Connected, but no tools declare a ui:// resource.");
      setUiTools(withUi);
    } catch (e) {
      setError(e instanceof McpError ? `MCP error ${e.code}: ${e.message}` : String(e));
    }
  }, [url]);

  const run = useCallback(
    async ({ tool, uiUri }: { tool: Tool; uiUri: string }) => {
      setError(null);
      setActive(tool.name);
      const client = clientRef.current!;
      try {
        const toolResult = await client.callTool(tool.name, {});
        const read = await client.readResource(uiUri);
        const bundleHtml = (read.contents[0] as { text?: string }).text ?? "";
        await mountApp({
          iframe: iframeRef.current!,
          sandboxOrigin,
          bundleUri: uiUri,
          bundleHtml,
          toolName: tool.name,
          toolArgs: {},
          toolResult,
          client,
        });
      } catch (e) {
        setError(e instanceof McpError ? `MCP error ${e.code}: ${e.message}` : String(e));
      }
    },
    [sandboxOrigin],
  );

  return (
    <main className="host">
      <header>
        <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="http://localhost:8888/mcp" />
        <button onClick={connect}>Connect</button>
      </header>
      {error && <p className="error">{error}</p>}
      <section className="cards">
        {uiTools.map((t) => (
          <ToolCard key={t.tool.name} tool={t.tool} uiUri={t.uiUri} onRun={() => run(t)} />
        ))}
      </section>
      <section className="stage" hidden={!active}>
        <iframe ref={iframeRef} title="mcp-app" className="app-frame" />
      </section>
    </main>
  );
}
