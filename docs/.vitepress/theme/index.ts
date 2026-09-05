import type { Theme } from "vitepress";
import DefaultTheme from "vitepress/theme";
import "./custom.css";

// ---------------------------------------------------------------------------
// Mermaid rendering (client only)
//
// VitePress/Shiki emit fences for unknown languages like:
//   <div class="language-mermaid vp-adaptive-theme">
//     <button class="copy"/><span class="lang">mermaid</span>
//     <pre class="shiki ..."><code>…source…</code></pre></div>
// i.e. the mermaid class lives on the WRAPPER and the <code> is class-less.
// We therefore scan wrapper divs (plus a plain-fence fallback), swap the whole
// wrapper for rendered SVG, and enforce fit via useMaxWidth + CSS so diagrams
// scale instead of overflowing or clipping.
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
        fontFamily: '"Noto Sans", sans-serif',
        flowchart: { useMaxWidth: true, curve: "linear", padding: 10 },
        sequence: { useMaxWidth: true },
        er: { useMaxWidth: true },
        state: { useMaxWidth: true },
        themeVariables: {
          fontSize: "15px",
          primaryColor: "#ffe0cf",
          primaryTextColor: "#381803",
          primaryBorderColor: "#d6530c",
          secondaryColor: "#fff0e4",
          tertiaryColor: "#ffffff",
          lineColor: "#a73c00",
          textColor: "#381803",
          clusterBkg: "#ffffff",
          clusterBorder: "#fac0a1",
          edgeLabelBackground: "#fff0e4",
          noteBkgColor: "#ffe0cf",
          noteBorderColor: "#d6530c",
          actorBkg: "#ffe0cf",
          actorBorder: "#d6530c",
          actorTextColor: "#381803",
          signalColor: "#a73c00",
          signalTextColor: "#381803",
          labelBoxBkgColor: "#fff0e4",
          labelBoxBorderColor: "#fac0a1",
          loopTextColor: "#381803",
          activationBkgColor: "#fac0a1",
        },
      });
      return mermaid as unknown as MermaidApi;
    });
  }
  return mermaidReady;
}

let renderSeq = 0;
let scheduled = false;
let fontPasses = 0;

interface PendingFence {
  replaceNode: HTMLElement;
  source: string;
}

/** Finds every unprocessed diagram fence, regardless of markup variant. */
function collectPending(): PendingFence[] {
  const out: PendingFence[] = [];

  // Primary: VitePress wrapper div carries the language class.
  document
    .querySelectorAll<HTMLElement>('div[class*="language-mermaid"]')
    .forEach((wrap) => {
      if ((wrap as HTMLElement & { __oqDone?: boolean }).__oqDone) return;
      const code = wrap.querySelector("code");
      const source = (code?.textContent ?? "").trim();
      if (source) out.push({ replaceNode: wrap, source });
      else (wrap as HTMLElement & { __oqDone?: boolean }).__oqDone = true;
    });

  // Fallback: plain <pre><code class="language-mermaid"> (non-Shiki paths).
  document
    .querySelectorAll<HTMLElement>("pre > code.language-mermaid")
    .forEach((code) => {
      if ((code as HTMLElement & { __oqDone?: boolean }).__oqDone) return;
      const source = (code.textContent ?? "").trim();
      if (source && code.parentElement) {
        out.push({ replaceNode: code.parentElement as HTMLElement, source });
      }
      (code as HTMLElement & { __oqDone?: boolean }).__oqDone = true;
    });

  return out;
}

async function renderPending() {
  const pending = collectPending();
  if (!pending.length) return;
  const mermaid = await loadMermaid();
  for (const fence of pending) {
    const holder = document.createElement("div");
    holder.className = "mermaid-wrap";
    try {
      const { svg } = await mermaid.render(`oq-mmd-${++renderSeq}`, fence.source);
      // Trusted: SVG generated locally by Mermaid from repo-authored docs.
      holder.innerHTML = svg;
    } catch (err) {
      console.error("[mermaid] render failed", err);
      const fallback = document.createElement("pre");
      fallback.className = "mermaid-fallback";
      fallback.textContent = fence.source;
      holder.append(fallback);
    }
    fence.replaceNode.replaceWith(holder);
  }
}

function scheduleRender() {
  if (scheduled) return;
  scheduled = true;
  setTimeout(() => {
    scheduled = false;
    void renderPending();
  }, 20);
}

/**
 * Mermaid measures text with whatever font is loaded at render time. Rendering
 * before Noto Sans arrives produces wrong-size boxes and clipped glyphs, so
 * the first paint waits for the font, and one extra pass heals any swap that
 * lands late.
 */
function renderWithFonts() {
  const want = ['400 15px "Noto Sans"', '700 15px "Noto Sans"', '800 15px "Noto Sans"'];
  const loads = async () => {
    try {
      await Promise.all(want.map((f) => document.fonts.load(f)));
      await document.fonts.ready;
    } catch {
      /* offline: fall back to whatever is available */
    }
    scheduleRender();
  };
  void loads();
  if (typeof document !== "undefined" && "fonts" in document) {
    void document.fonts.ready.then(() => {
      if (fontPasses < 1) {
        fontPasses += 1;
        scheduleRender();
      }
    });
  }
}

export default {
  extends: DefaultTheme,
  enhanceApp() {
    if (typeof window === "undefined" || typeof document === "undefined") return;

    const start = () => {
      const observer = new MutationObserver(scheduleRender);
      observer.observe(document.body, { childList: true, subtree: true });
      renderWithFonts(); // font-gated initial paint
      window.addEventListener("load", scheduleRender);
    };

    if (document.body) start();
    else document.addEventListener("DOMContentLoaded", start, { once: true });
  },
} as Theme;
