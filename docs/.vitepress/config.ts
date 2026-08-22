import { defineConfig } from "vitepress";

// Self-hosted Noto fonts (no external CDNs); declared in theme/custom.css.
const fontPreload: (string | Record<string, unknown>)[][] = [
  ["link", { rel: "preload", href: "/fonts/noto-sans-latin-var.woff2", as: "font", type: "font/woff2", crossorigin: "" }],
  [
    "link",
    {
      rel: "preload",
      href: "/fonts/noto-sans-mono-latin-var.woff2",
      as: "font",
      type: "font/woff2",
      crossorigin: "",
    },
  ],
];

export default defineConfig({
  title: "OpenQuiz",
  description:
    "The open-source, real-time coding quiz platform with a built-in Online Judge engine.",
  lang: "en-US",
  head: fontPreload,
  // Mermaid ships as one intentionally large lazy-loaded chunk.
  build: { chunkSizeWarningLimit: 1500 },
  themeConfig: {
    nav: [
      { text: "Home", link: "/" },
      { text: "Getting Started", link: "/getting-started" },
      { text: "Architecture", link: "/architecture" },
      { text: "API", link: "/api-reference" },
    ],
    sidebar: [
      {
        text: "Introduction",
        items: [
          { text: "Getting Started", link: "/getting-started" },
          { text: "Player Guide", link: "/player-guide" },
          { text: "Admin Guide", link: "/admin-guide" },
        ],
      },
      {
        text: "System",
        items: [
          { text: "Architecture", link: "/architecture" },
          { text: "Database Schema", link: "/database" },
        ],
      },
      {
        text: "Reference",
        items: [
          { text: "API Reference", link: "/api-reference" },
          { text: "WebSocket Protocol", link: "/websocket-protocol" },
          { text: "Deployment", link: "/deployment" },
          { text: "Contributing", link: "/contributing" },
        ],
      },
    ],
    socialLinks: [
      { icon: "github", link: "https://github.com/richie-rich90454/openquiz" },
    ],
    search: { provider: "local" },
  },
});
