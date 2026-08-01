import { useCallback, useRef, useState } from "react";
import { getToolUiResourceUri } from "@modelcontextprotocol/ext-apps/app-bridge";
import type { Tool } from "@modelcontextprotocol/sdk/types.js";
import { DevHostMcpClient, McpError, type Exchange } from "../mcp/transport";
import { mountApp } from "../host/bridge";
import { PROTOCOL_VERSION } from "../mcp/envelope";
import { ToolCard } from "./ToolCard";
import { Inspector } from "./Inspector";

const UI_CAP = "io.modelcontextprotocol/ui";
const DEFAULT_URL = "http://localhost:8080/mcp";

type Connection = "idle" | "connecting" | "connected" | "error";
interface UiTool {
  tool: Tool;
  uiUri: string;
}
interface Stage {
  name: string;
  uri: string;
  mime?: string;
}

export function App() {
  const [url, setUrl] = useState(DEFAULT_URL);
  const [conn, setConn] = useState<Connection>("idle");
  const [error, setError] = useState<string | null>(null);
  const [uiTools, setUiTools] = useState<UiTool[]>([]);
  const [exchanges, setExchanges] = useState<Exchange[]>([]);
  const [stage, setStage] = useState<Stage | null>(null);

  const clientRef = useRef<DevHostMcpClient | null>(null);
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const mountRef = useRef<{ dispose(): void } | null>(null);
  const [sandboxOrigin, setSandboxOrigin] = useState("http://localhost:5174");

  const record = useCallback((ex: Exchange) => {
    setExchanges((prev) => {
      const i = prev.findIndex((e) => e.id === ex.id);
      if (i === -1) return [...prev, ex];
      const next = prev.slice();
      next[i] = ex;
      return next;
    });
  }, []);

  const connect = useCallback(async () => {
    setError(null);
    setUiTools([]);
    setStage(null);
    setExchanges([]);
    mountRef.current?.dispose();
    mountRef.current = null;
    setConn("connecting");
    try {
      const origin = (await (await fetch("/api/sandbox-origin")).json()).origin as string;
      setSandboxOrigin(origin);
      const client = new DevHostMcpClient(url, record);
      clientRef.current = client;

      const discover = await client.discover();
      if (!discover.capabilities.extensions?.[UI_CAP]) {
        setConn("error");
        setError("This server doesn't advertise the MCP Apps capability (io.modelcontextprotocol/ui).");
        return;
      }
      const tools = await client.listTools();
      const withUi = tools
        .map((tool) => ({ tool, uiUri: getToolUiResourceUri(tool) }))
        .filter((t): t is UiTool => Boolean(t.uiUri));
      setUiTools(withUi);
      setConn("connected");
      if (withUi.length === 0) {
        setError("Connected, but no tools declare a ui:// resource.");
      }
    } catch (e) {
      setConn("error");
      setError(e instanceof McpError ? `MCP error ${e.code}: ${e.message}` : String(e));
    }
  }, [url, record]);

  const run = useCallback(
    async ({ tool, uiUri }: UiTool) => {
      const client = clientRef.current;
      if (!client) return;
      setError(null);
      try {
        const toolResult = await client.callTool(tool.name, {});
        const read = await client.readResource(uiUri);
        const content = read.contents[0] as { text?: string; mimeType?: string } | undefined;
        if (!content || typeof content.text !== "string") {
          setError(`Resource ${uiUri} returned no text content to render.`);
          return;
        }
        // Tear down any previously mounted app before mounting the next.
        mountRef.current?.dispose();
        setStage({ name: tool.name, uri: uiUri, mime: content.mimeType });
        mountRef.current = await mountApp({
          iframe: iframeRef.current!,
          sandboxOrigin,
          bundleUri: uiUri,
          bundleHtml: content.text,
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
    <div className="app">
      <div className="wireline" />
      <header className="bar">
        <div className="brand">
          <span className="mark">◇ mocapi</span>
          <span className="sub">MCP Apps dev host</span>
        </div>
        <label className="address">
          <span className="scheme">POST</span>
          <input
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder={DEFAULT_URL}
            spellCheck={false}
            aria-label="MCP server URL"
          />
        </label>
        <button className="btn" onClick={connect} disabled={conn === "connecting"}>
          {conn === "connecting" ? "Connecting…" : "Connect"}
        </button>
        <div className="status">
          <span className={`dot${conn === "connected" ? " on" : ""}`} />
          {conn === "idle" && <span>not connected</span>}
          {conn === "connecting" && <span>server/discover…</span>}
          {conn === "connected" && (
            <>
              <span>{PROTOCOL_VERSION}</span>
              <span className="chip-ok">ui ✓</span>
              <span>
                {uiTools.length} UI tool{uiTools.length === 1 ? "" : "s"}
              </span>
            </>
          )}
          {conn === "error" && <span className="err">connection failed</span>}
        </div>
      </header>

      <div className="body">
        <main className="main">
          {error && <div className="banner-err">{error}</div>}

          {conn === "connected" && uiTools.length > 0 && (
            <section>
              <p className="eyebrow">Tools with a UI</p>
              <div className="cards">
                {uiTools.map((t) => (
                  <ToolCard
                    key={t.tool.name}
                    tool={t.tool}
                    uiUri={t.uiUri}
                    active={stage?.name === t.tool.name}
                    onRun={() => run(t)}
                  />
                ))}
              </div>
            </section>
          )}

          {conn === "idle" && (
            <div className="empty">
              <strong>Point at a mocapi server</strong>
              Enter its <code>/mcp</code> URL and Connect to discover UI-bearing tools.
            </div>
          )}

          <section className="stage" hidden={!stage}>
            <div className="stage-head">
              <span className="dot on" />
              <span className="uri">{stage?.uri}</span>
              <span className="mime">{stage?.mime}</span>
            </div>
            <iframe ref={iframeRef} title="mcp-app" className="app-frame" />
          </section>
        </main>

        <Inspector exchanges={exchanges} onClear={() => setExchanges([])} />
      </div>
    </div>
  );
}
