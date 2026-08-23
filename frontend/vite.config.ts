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
    // core-js polyfilled bundle for the Chrome 60 / Safari 11 / Firefox 60
    // floor. Rolldown cannot lower to ES2015 without crashing, so Chrome 49
    // is not a viable floor on this toolchain.
    legacy({
      targets: ["chrome >= 60", "firefox >= 60", "safari >= 11", "ios_saf >= 11", "edge >= 79"],
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
    cssTarget: "chrome60",
  },
});
