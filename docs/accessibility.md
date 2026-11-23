# Accessibility platform (WCAG 2.2 AA)

Cross-cutting behavior lives in `frontend/src/App.tsx` and `frontend/src/index.css`.
Screen-level markup stays in each screen's owner.

## Provided globally

- **Skip link** — first focusable element; focuses the rendered `<main>` and
  assigns it `id="main-content"` and `tabindex="-1"` if absent.
- **Route focus + announcements** — `router.subscribe("onResolved")` moves focus
  to `<main>` and writes the new page name into a polite `role="status"` region.
  The initial load is silent to avoid a redundant announcement.
- **Focus indicator** — 2px `:focus-visible` outline with 2px offset, 3px ink
  outline on answer tiles, and a light outline on the dark stage. In
  `forced-colors` mode the system `Highlight` color is used.
- **Contrast** — tokens are light-only; `--oq-ink-soft` and answer colors meet
  4.5:1 against their backgrounds. `prefers-contrast: more` darkens ink, hardens
  borders and raises control border width.
- **Reduced motion** — both `prefers-reduced-motion: reduce` and the in-app
  `motion-reduced` class collapse CSS animation/transition duration to ~0 and
  disable scroll animation. The GSAP layer is already driven by the same flag.
- **Target size** — controls are at least 44px tall; the locale switcher and
  update banner follow the same rule.
- **RTL** — `dir` is set on `<html>`; CSS uses logical properties and table/
  answer text alignment is `start`, so Arabic mirrors without per-screen CSS.

## Screen-owner responsibilities

1. Label every icon-only button with `aria-label` and mark the icon `aria-hidden`.
2. Associate inputs with visible `<label>`; use `aria-describedby` for hints and
   `aria-invalid` + `role="alert"` for errors.
3. Give each table a caption or `aria-label`; give sortable headers `aria-sort`.
4. Keep drag-sort questions keyboard-operable (move up/down buttons).
5. Use Radix Dialog/Tabs for focus trapping and arrow-key semantics rather than
   hand-rolled overlays.
6. Call `useI18n().t(key)` for new user-facing strings (see `i18n.md`).

## Verification

- Keyboard-only walkthrough: landing → join → host/player → results, using Tab,
  Shift+Tab, Enter, Space and Escape only.
- `@axe-core/playwright` in `frontend/e2e` for automated rule checks.
- Emulate `prefers-reduced-motion: reduce` and `forced-colors: active`.
