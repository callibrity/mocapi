/**
 * Get Time MCP App — the in-iframe React UI.
 *
 * Adapted (trimmed to the single "Get Server Time" action) from the official
 * MCP Apps React example, MIT © the Model Context Protocol authors:
 * https://github.com/modelcontextprotocol/ext-apps/tree/main/examples/basic-server-react
 *
 * `useApp` connects to the host over the postMessage bridge; the button calls
 * the server's `get-time` tool (served and linked by mocapi) and renders the
 * `{ time }` result. mocapi serves these bytes; everything here runs in the
 * host-controlled sandboxed iframe.
 */
import type { App } from "@modelcontextprotocol/ext-apps";
import { useApp } from "@modelcontextprotocol/ext-apps/react";
import type { CallToolResult } from "@modelcontextprotocol/sdk/types.js";
import { StrictMode, useCallback, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import styles from "./mcp-app.module.css";

function extractTime(result: CallToolResult): string {
  // Prefer the structured field (the tool declares an outputSchema of { time }).
  const structured = result.structuredContent as { time?: string } | undefined;
  if (typeof structured?.time === "string") {
    return structured.time;
  }
  // Fall back to the text block for tools that only return unstructured content.
  const text = result.content?.find((c) => c.type === "text");
  return text?.text ?? "[no time in result]";
}

/** Split an ISO-8601 instant into a big HH:MM:SS clock and a date, both UTC. */
function splitInstant(iso: string): { clock: string; date: string; iso: string | null } {
  const m = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/.exec(iso);
  if (!m) return { clock: iso, date: "", iso: null };
  return { clock: m[2], date: m[1], iso };
}

function GetTimeApp() {
  const [toolResult, setToolResult] = useState<CallToolResult | null>(null);

  // `useApp` (1) creates the App, (2) registers handlers via onAppCreated, and
  // (3) connects to the host.
  const { app, error } = useApp({
    appInfo: { name: "Get Time App", version: "1.0.0" },
    capabilities: {},
    onAppCreated: (created) => {
      // The host may deliver the initiating tool's result up front.
      created.ontoolresult = async (result) => setToolResult(result);
      created.onerror = console.error;
    },
  });

  if (error) {
    return (
      <div className={styles.main}>
        <strong>ERROR:</strong> {error.message}
      </div>
    );
  }
  if (!app) {
    return <div className={styles.main}>Connecting…</div>;
  }
  return <GetTimeInner app={app} initialResult={toolResult} />;
}

function GetTimeInner({ app, initialResult }: { app: App; initialResult: CallToolResult | null }) {
  const [serverTime, setServerTime] = useState("—");

  useEffect(() => {
    if (initialResult) {
      setServerTime(extractTime(initialResult));
    }
  }, [initialResult]);

  const handleGetTime = useCallback(async () => {
    try {
      const result = await app.callServerTool({ name: "get-time", arguments: {} });
      setServerTime(extractTime(result));
    } catch (e) {
      console.error(e);
      setServerTime("[ERROR]");
    }
  }, [app]);

  const { clock, date, iso } = splitInstant(serverTime);
  return (
    <main className={styles.main}>
      <div className={styles.card}>
        <p className={styles.eyebrow}>◷ Server time · UTC</p>
        <p className={styles.clock}>{clock}</p>
        {date && <p className={styles.date}>{date}</p>}
        {iso && <p className={styles.iso}>{iso}</p>}
        <button className={styles.button} onClick={handleGetTime}>
          Get Server Time
        </button>
        <p className={styles.footer}>Served by mocapi · React + Vite</p>
      </div>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <GetTimeApp />
  </StrictMode>,
);
