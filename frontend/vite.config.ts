import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

export default defineConfig({
    plugins: [
        tailwindcss(),
        react({
            babel: {
                plugins: [["babel-plugin-react-compiler", {}]],
            },
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
    test: {
        environment: "jsdom",
        include: ["src/**/*.test.ts"],
        // Unit tests never load the real 3MB Monaco bundle (or its worker):
        // the stub below implements the exact surface CodeEditor uses.
        alias: [
            {
                find: /^monaco-editor(\/.*)?(\?.*)?$/,
                replacement: fileURLToPath(new URL("./test/mocks/monaco.ts", import.meta.url)),
            },
        ],
        coverage: {
            provider: "v8",
            include: ["src/**/*.ts"],
            exclude: ["src/**/*.test.ts", "src/vite-env.d.ts"],
            thresholds: { lines: 100, branches: 100, functions: 100, statements: 100 },
        },
    },
});
