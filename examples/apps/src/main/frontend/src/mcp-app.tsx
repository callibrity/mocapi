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
  const text = result.content?.find((c) => c.type === "text");
  return text?.text ?? "[no time in result]";
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

  return (
    <main className={styles.main}>
      <p className={styles.notice}>Served by mocapi · built with React + Vite</p>
      <p>
        <strong>Server time:</strong> <code className={styles.time}>{serverTime}</code>
      </p>
      <button className={styles.button} onClick={handleGetTime}>
        Get Server Time
      </button>
    </main>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <GetTimeApp />
  </StrictMode>,
);
