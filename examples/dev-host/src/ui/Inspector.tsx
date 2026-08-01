import { useState } from "react";
import type { Exchange } from "../mcp/transport";
import { PROTOCOL_VERSION } from "../mcp/envelope";
import { prettyJson } from "./json";

function routingHeaders(ex: Exchange): [string, string][] {
  const chips: [string, string][] = [
    ["MCP-Protocol-Version", PROTOCOL_VERSION],
    ["Mcp-Method", ex.method],
  ];
  if (ex.mcpName) chips.push(["Mcp-Name", ex.mcpName]);
  return chips;
}

function statusLabel(ex: Exchange): string {
  if (ex.status === "pending") return "···";
  if (ex.status === "error") return `err ${ex.error?.code ?? ""}`.trim();
  return "ok";
}

function Frame({ ex }: { ex: Exchange }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={`frame ${ex.status}${open ? " open" : ""}`}>
      <button className="frame-head" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <span className="sig" />
        <span className="method">{ex.method}</span>
        {ex.mcpName && <span className="name">· {ex.mcpName}</span>}
        <span className="meta">
          {ex.ms != null && <span>{ex.ms}ms</span>}
          <span className={ex.status === "error" ? "st-error" : ex.status === "ok" ? "st-ok" : ""}>
            {statusLabel(ex)}
          </span>
          <span className="caret">▸</span>
        </span>
      </button>
      {open && (
        <div className="frame-body">
          <div className="chips">
            {routingHeaders(ex).map(([k, v]) => (
              <span className="hchip" key={k}>
                <b>{k}</b> {v}
              </span>
            ))}
          </div>
          <div>
            <span className="pane-label out">▲ request</span>
            <pre className="json">{prettyJson(ex.request)}</pre>
          </div>
          {ex.status !== "pending" && (
            <div>
              <span className={`pane-label ${ex.status === "error" ? "err" : "in"}`}>
                ▼ {ex.status === "error" ? "error" : "response"}
              </span>
              <pre className="json">
                {prettyJson(ex.status === "error" ? (ex.response ?? { error: ex.error }) : ex.response)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function Inspector({ exchanges, onClear }: { exchanges: Exchange[]; onClear: () => void }) {
  return (
    <aside className="wire">
      <div className="wire-head">
        <h2>Wire</h2>
        <span className="count">{exchanges.length}</span>
        <span className="spacer" />
        {exchanges.length > 0 && (
          <button className="btn btn-ghost" onClick={onClear}>
            Clear
          </button>
        )}
      </div>
      <div className="wire-legend">
        <span className="lg out">request</span>
        <span className="lg in">response</span>
        <span className="lg err">error</span>
      </div>
      <div className="wire-list">
        {exchanges.length === 0 ? (
          <div className="wire-empty">No traffic yet — connect to a server.</div>
        ) : (
          [...exchanges].reverse().map((ex) => <Frame key={ex.id} ex={ex} />)
        )}
      </div>
    </aside>
  );
}
