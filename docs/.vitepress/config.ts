import { defineConfig } from "vitepress";

export default defineConfig({
  title: "OpenQuiz",
  description: "The open-source, real-time coding quiz platform with a built-in Online Judge engine.",
  lang: "en-US",
  themeConfig: {
    nav: [
      { text: "Home", link: "/" },
      { text: "Getting Started", link: "/getting-started" },
      { text: "Admin Guide", link: "/admin-guide" },
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
        text: "Reference",
        items: [
          { text: "API Reference", link: "/api-reference" },
          { text: "WebSocket Protocol", link: "/websocket-protocol" },
          { text: "Deployment", link: "/deployment" },
          { text: "Contributing", link: "/contributing" },
        ],
      },
    ],
    socialLinks: [{ icon: "github", link: "https://github.com/richie-rich90454/openquiz" }],
    search: { provider: "local" },
  },
});
