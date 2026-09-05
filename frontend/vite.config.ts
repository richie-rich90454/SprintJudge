import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";

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
        coverage: {
            provider: "v8",
            include: ["src/**/*.ts"],
            exclude: ["src/**/*.test.ts", "src/vite-env.d.ts"],
            thresholds: { lines: 100, branches: 100, functions: 100, statements: 100 },
        },
    },
});
