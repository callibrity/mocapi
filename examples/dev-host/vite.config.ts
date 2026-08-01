import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

// INPUT selects which entry to build (index.html or sandbox.html). The build/dev
// scripts always set it; default to the host page so tooling that loads this config
// without it (e.g. Vitest, which has no `INPUT`) doesn't throw at import time.
const INPUT = process.env.INPUT ?? "index.html";
const isDev = process.env.NODE_ENV === "development";

export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    sourcemap: isDev ? "inline" : undefined,
    cssMinify: !isDev,
    minify: !isDev,
    rollupOptions: { input: INPUT },
    outDir: "dist",
    emptyOutDir: false,
  },
});
