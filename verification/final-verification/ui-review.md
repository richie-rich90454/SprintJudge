# UI review — new platform screens (read-only)

## Medium
- `QuestionWizard.tsx:368-377,451-453,469-472` — Step buttons show active state by colour only (no `aria-current`/`aria-pressed`); advancing unmounts the focused button, dropping keyboard focus to `<body>` inside the modal.
- `AssignmentView.tsx:117-124` — List/Create tabs are styled buttons with no tab/pressed semantics; active tab is not exposed to assistive tech.
- `AssignmentView.tsx:236-266` — No `progress.isError`/`roster.isError` handling: failed fetches render "No progress yet." / "0 students in roster" (`:258,266`), misreporting errors as empty data.
- `AnalyticsView.tsx:176-283` — Students and Items sections have `isPending` but no `isError`/Retry (contrast Class summary `:117-125`); failures render blank panels.
- `ShareResultCard.tsx:43` — `navigator.clipboard.writeText` used unguarded; on insecure origins `navigator.clipboard` is undefined, so "Copy summary" silently fails (unlike `HostView.tsx:578-596`, which has a legacy fallback).
- `ProfileView.tsx:93` — `String(create.error.message)` without `?.` or type guard: non-Error rejections render "undefined" or throw.

## Low
- `ShareResultCard.tsx:69-73` — `<a>` is never appended to the DOM and `revokeObjectURL` runs immediately after `click()`; Firefox/Safari may cancel the SVG download.
- `AnalyticsView.tsx:145,273,275,352` — `.length`/`.toFixed()`/`Object.entries()` called directly on API fields; a partial response throws during render (no optional chaining).
- No component tests cover these seven screens/components. Only helpers/services are tested (`richStem.test.ts`, `itemQuality*.test.ts`, `AssignmentApiService.test.ts`, `AnalyticsApiService.test.ts`, `ProfileApiService.test.ts`); `ResultView.test.tsx` does not exercise `ShareResultCard` or `CollectionsMenu`.

## OK
Responsive grids and `overflow-x-auto`; `role="switch"`/`aria-checked` (`FairnessControls.tsx:76`), `aria-pressed` (`StageTheme.tsx:99`), labelled inputs, Radix dialog focus trap, skeletons/alerts on primary queries. No regressions found in join/play/host flows.
