import type { Theme } from "vitepress";
import DefaultTheme from "vitepress/theme";
import "./custom.css";

// ---------------------------------------------------------------------------
// Mermaid rendering (client only)
//
// Strategy: a debounced MutationObserver watches the document for any
// <pre><code class="language-mermaid"> fence that Vue injects — regardless of
// navigation timing, async page components, or hmr — replaces it with rendered
// SVG, and guarantees fit: useMaxWidth + CSS force max-width:100%/height:auto,
// so diagrams scale down instead of overflowing or clipping.
// ---------------------------------------------------------------------------

type MermaidApi = {
  initialize: (cfg: Record<string, unknown>) => void;
  render: (id: string, source: string) => Promise<{ svg: string }>;
};

let mermaidReady: Promise<MermaidApi> | null = null;

function loadMermaid(): Promise<MermaidApi> {
  if (!mermaidReady) {
    mermaidReady = import("mermaid").then(({ default: mermaid }) => {
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: "strict",
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
      return mermaid as unknown as MermaidApi;
    });
  }
  return mermaidReady;
}

let renderSeq = 0;
let scheduled = false;

function findPendingFences(): HTMLElement[] {
  return Array.from(
    document.querySelectorAll<HTMLElement>("pre > code.language-mermaid")
  ).filter((el) => !(el as HTMLElement & { __oqDone?: boolean }).__oqDone);
}

async function renderPending() {
  const blocks = findPendingFences();
  if (!blocks.length) return;
  const mermaid = await loadMermaid();
  for (const code of blocks) {
    const marker = code as HTMLElement & { __oqDone?: boolean };
    marker.__oqDone = true;                       // never process twice
    const pre = code.parentElement as HTMLElement;
    if (!pre) continue;
    const source = code.textContent ?? "";
    const holder = document.createElement("div");
    holder.className = "mermaid-wrap";
    try {
      const { svg } = await mermaid.render(`oq-mmd-${++renderSeq}`, source);
      // Trusted: SVG is generated locally by Mermaid from repo-authored docs.
      holder.innerHTML = svg;
    } catch (err) {
      console.error("[mermaid] render failed", err);
      const fallback = document.createElement("pre");
      fallback.className = "mermaid-fallback";
      fallback.textContent = source;
      holder.append(fallback);
    }
    pre.replaceWith(holder);
  }
}

function scheduleRender() {
  if (scheduled) return;
  if (!findPendingFences().length) return;      // nothing to do: stop the loop
  scheduled = true;
  setTimeout(() => {
    scheduled = false;
    void renderPending();
  }, 20);
}

export default {
  extends: DefaultTheme,
  enhanceApp() {
    if (typeof window === "undefined" || typeof document === "undefined") return;

    const start = () => {
      const observer = new MutationObserver(scheduleRender);
      observer.observe(document.body, { childList: true, subtree: true });
      scheduleRender();                          // initial paint
      window.addEventListener("load", scheduleRender);
    };

    if (document.body) start();
    else document.addEventListener("DOMContentLoaded", start, { once: true });
  },
} as Theme;
