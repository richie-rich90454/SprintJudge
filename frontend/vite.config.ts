import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import legacy from "@vitejs/plugin-legacy";

export default defineConfig({
  plugins: [
    tailwindcss(),
    react({
      babel: {
        plugins: [["babel-plugin-react-compiler", {}]],
      },
    }),
    // Dual-bundle output: modern ESM for evergreen browsers, SystemJS +
    // core-js polyfilled bundle for Chrome 49 / Safari 10 / Firefox 52 era.
    legacy({
      // Legacy SystemJS chunks are temporarily disabled: plugin-legacy@8 +
      // Vite 8's Rolldown pipeline crashes on generated spreads (upstream bug).
      // Evergreen targets are unaffected; flip this back to true once the
      // upstream fix lands and Chrome-49-era support is needed again.
      renderLegacyChunks: false,
      targets: ["chrome >= 96", "firefox >= 90", "safari >= 15", "edge >= 96"],
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
