import type { Tool } from "@modelcontextprotocol/sdk/types.js";

export function ToolCard({ tool, uiUri, onRun }: { tool: Tool; uiUri: string; onRun: () => void }) {
  return (
    <button className="card" onClick={onRun}>
      <h3>{tool.title ?? tool.name}</h3>
      {tool.description && <p>{tool.description}</p>}
      <code>{uiUri}</code>
    </button>
  );
}
