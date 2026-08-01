import type { Tool } from "@modelcontextprotocol/sdk/types.js";

export function ToolCard({
  tool,
  uiUri,
  active,
  onRun,
}: {
  tool: Tool;
  uiUri: string;
  active: boolean;
  onRun: () => void;
}) {
  return (
    <button className="card" aria-current={active ? "true" : undefined} onClick={onRun}>
      <h3>{tool.title ?? tool.name}</h3>
      {tool.description && <p>{tool.description}</p>}
      <span className="uri">{uiUri}</span>
    </button>
  );
}
