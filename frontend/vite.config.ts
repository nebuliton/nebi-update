import { resolve } from "node:path";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  base: "/",
  plugins: [react()],
  build: {
    outDir: resolve(__dirname, "../src/main/resources/dashboard"),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: "assets/dashboard.js",
        chunkFileNames: "assets/[name].js",
        assetFileNames: (assetInfo) => {
          if (assetInfo.name?.endsWith(".css")) {
            return "assets/dashboard.css";
          }
          return "assets/[name][extname]";
        }
      }
    }
  }
});
