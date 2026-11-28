# Frontend bug audit (read-only, static review)

C=confirmed by inspection; S=speculative. Ranked by severity.

1. **C** `App.tsx:47-51` + `public/sw.js:40`: first visit's `clients.claim()` fires `controllerchange` → unconditional reload mid-typing. Fix: reload only for a waiting worker; or drop `clients.claim()`.
2. **C** `App.tsx:31,97,106,125`: only 6 `t()` calls; screens hardcode English, so Arabic flips RTL but not content. Fix: use `t()` in screens; expand `en/ar`.
3. **C** `MusicToggle.tsx:18,28-36`: persisted `oq-music=on` never starts music after reload. Fix: mount effect resume/startMusic when `on && sound==="on"`; cleanup `stopMusic`.
4. **C** `ResultView.tsx:95-100`, `AdminDashboard.tsx:278-283`: `revokeObjectURL` right after `click()` cancels downloads. Fix: defer revoke as `ShareResultCard.tsx:89` does.
5. **C** `FillBlankRenderer.ts:7-24`, `NumericRenderer.ts:7-24`: no initial emit; `QuestionView.tsx:434` submits on expiry regardless of `hasResponse` → null answers. Fix: emit initial value.
6. **C** `QuestionView.tsx:552`: `revealSignal=0` always, so `reveal()` never runs; MCQ/MultiSelect never highlight correctness. Fix: increment on REVIEW.
7. **C** `QuestionView.tsx:444-448`: code draft deleted at submit; queued/failed WS or OJ retry loses work. Fix: clear on `SUBMISSION_RESULT` ack.
8. **C** `DragSortRenderer.ts:32-46`: drag/drop only; keyboard users cannot reorder. Fix: add aria-labeled up/down buttons.
9. **C** `AnalyticsView.tsx:47-59,96-110`: `sessionId` in queryKey fetches per keystroke; Apply filter is decorative. Fix: separate `appliedSessionId` state.
10. **C** `App.tsx:96` + `focusMain:10-17`: skip-link target `#main-content` absent until first route change. Fix: set id/tabindex at boot.
11. **C** `ErrorBoundary.tsx:26-36`: untranslated fallback, reload-only. Fix: add keys and retry.
12. **S** `AssignmentView.tsx:117-136`: custom tabs lack arrow-key handling/`aria-controls`. Fix: reuse Radix `Tabs`.
13. **S** `QuestionRendererHost.tsx:36`: `onResponse` in deps; inline callback (`QuestionWizard.tsx:514`) remounts renderer, wiping code. Fix: ref the callback.
14. **S** `adminGuard.ts:17-22`: non-auth failure throws in `beforeLoad` with no route `errorComponent`; comment claims redirect. Fix: add error component, align comment.
15. **S** `App.tsx:143-145`: motion effect keyed on store value reads `matchMedia` live; OS flip leaves stale class. Fix: subscribe to change.
