import type { Theme } from "vitepress";
import DefaultTheme from "vitepress/theme";
import "./custom.css";

let mermaidReady: Promise<typeof import("mermaid")> | null = null;

function loadMermaid() {
  if (!mermaidReady) {
    mermaidReady = import("mermaid").then(({ default: mermaid }) => {
      // Flat blueprint theme on the #3255A4 accent. useMaxWidth makes every
      // SVG scale to its container, so wide diagrams shrink instead of
      // overflowing or getting clipped.
      mermaid.initialize({
        startOnLoad: false,
        theme: "base",
        fontFamily: '"Noto Sans", ui-sans-serif, system-ui, sans-serif',
        flowchart: { useMaxWidth: true, curve: "basis", padding: 10 },
        sequence: { useMaxWidth: true },
        er: { useMaxWidth: true },
        state: { useMaxWidth: true },
        themeVariables: {
          fontSize: "15px",
          primaryColor: "#e8eefb",
          primaryTextColor: "#1a1f2e",
          primaryBorderColor: "#3255a4",
          secondaryColor: "#f0f3f8",
          tertiaryColor: "#f8faff",
          lineColor: "#5f6368",
          textColor: "#1a1f2e",
          clusterBkg: "#f8faff",
          clusterBorder: "#c0c8d8",
          edgeLabelBackground: "#ffffff",
          noteBkgColor: "#fff8e1",
          noteBorderColor: "#f57c00",
          actorBkg: "#e8eefb",
          actorBorder: "#3255a4",
          actorTextColor: "#1a1f2e",
          signalColor: "#5f6368",
          signalTextColor: "#1a1f2e",
          labelBoxBkgColor: "#f0f3f8",
          labelBoxBorderColor: "#c0c8d8",
          loopTextColor: "#1a1f2e",
          activationBkgColor: "#d7e2f7",
        },
      });
      return mermaid;
    });
  }
  return mermaidReady;
}

let renderSeq = 0;

async function renderMermaidBlocks() {
  const blocks = document.querySelectorAll<HTMLElement>(
    'pre > code.language-mermaid'
  );
  if (!blocks.length) return;
  const mermaid = await loadMermaid();
  for (const code of Array.from(blocks)) {
    const pre = code.parentElement as HTMLElement;
    const source = code.textContent ?? "";
    const holder = document.createElement("div");
    holder.className = "mermaid-wrap";
    pre.replaceWith(holder);
    try {
      const id = `oq-mermaid-${++renderSeq}`;
      const { svg } = await mermaid.render(id, source);
      holder.innerHTML = svg; // trusted: generated locally by Mermaid
    } catch (err) {
      holder.innerHTML =
        '<pre class="mermaid-error">Diagram failed to render.</pre>';
      console.error(err);
    }
  }
}

export default {
  extends: DefaultTheme,
  enhanceApp({ router }) {
    if (typeof window === "undefined" || !router) return;
    const previous = router.onAfterRouteChanged?.bind(router);
    router.onAfterRouteChanged = () => {
      previous?.();
      void renderMermaidBlocks();
    };
    // Initial load: the first route change fires this too, but be safe.
    if (document.readyState !== "loading") {
      setTimeout(() => void renderMermaidBlocks(), 0);
    } else {
      window.addEventListener("DOMContentLoaded", () =>
        setTimeout(() => void renderMermaidBlocks(), 0)
      );
    }
  },
} as Theme;
