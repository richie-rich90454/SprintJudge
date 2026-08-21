import { defineConfig } from "vitepress";

// Pure Noto Sans (UI) + Noto Sans Mono (code), loaded once for the whole site.
const notoHead: (string | Record<string, unknown>)[][] = [
  ["link", { rel: "preconnect", href: "https://fonts.googleapis.com" }],
  ["link", { rel: "preconnect", href: "https://fonts.gstatic.com", crossorigin: "" }],
  [
    "link",
    {
      rel: "stylesheet",
      href:
        "https://fonts.googleapis.com/css2?family=Noto+Sans:wght@400;500;600;700&family=Noto+Sans+Mono:wght@400;600&display=swap",
    },
  ],
];

export default defineConfig({
  title: "OpenQuiz",
  description:
    "The open-source, real-time coding quiz platform with a built-in Online Judge engine.",
  lang: "en-US",
  head: notoHead,
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
