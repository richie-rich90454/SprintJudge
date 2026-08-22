import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import legacy from "@vitejs/plugin-legacy";

export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: [["babel-plugin-react-compiler", {}]],
      },
    }),
    // Dual-bundle output: modern ESM for evergreen browsers, SystemJS +
    // core-js polyfilled bundle for Chrome 49 / Safari 10 / Firefox 52 era.
    legacy({
      targets: ["chrome >= 49", "firefox >= 52", "safari >= 10", "ios_saf >= 10", "edge >= 79"],
      modernPolyfills: true,
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/ws": { target: "ws://localhost:8080", ws: true },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
    // JS target is owned by @vitejs/plugin-legacy; CSS stays at the Chrome 49 floor.
    cssTarget: "chrome49",
  },
});
