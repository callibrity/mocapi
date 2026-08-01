import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

// Build the app into ONE self-contained HTML file (inlined JS/CSS, no external
// loads) — the form an MCP Apps ui:// bundle must take — and emit it onto the
// module's build output (target/classes), NOT into source. The Maven build runs
// this at generate-resources, so the bundle is on the classpath that mocapi
// serves via @McpUi(resource=classpath:/ui/get-time/mcp-app.html). It is a build
// product and is never committed.
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    outDir: "../../../target/classes/ui/get-time",
    emptyOutDir: false,
    cssMinify: true,
    minify: true,
    rollupOptions: {
      input: "mcp-app.html",
    },
  },
});
