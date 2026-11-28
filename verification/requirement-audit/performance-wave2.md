# Bank runtime performance audit (329,652 questions / 4,594 files / 831 MB)

All figures are from a local Jackson 2.20.1 harness reproducing the exact load paths; timings vary with page-cache warmth.

## Ranked findings

1. **TeachNextService.recommendPractice — ~1,022 full-bank scans per request** (`TeachNextService.java:99-110`, `unitsOf:176`, `topicMatches:190`). It iterates all 82 subjects and 940 subject/unit pairs; each `bankRecords.records(...)` miss re-parses the whole bank. With the 2-entry LRU (`BankRecordSource.java:30,36-41`) every slice is cold. Measured cost of one full scan: **4.1 s warm / 8.4 s cold** ⇒ minutes to hours per request. **Confirmed (code + measured scan).** Fix: build one lazy subject→unit→topic index in `BankRecordSource` from a single pass and serve `unitsOf`/`topicMatches` from it.

2. **BankRecordSource.records() cold scan** (`BankRecordSource.java:68-84`) — reads all 4,594 files and `convertValue`s all 329,652 records to return one slice; measured **4.1–8.4 s / ~52–176 MB** per miss. Hits `build`, `buildSet`, `itemReports`, `playable`. **Measured.** Fix: one-pass index keyed by subject/unit, cache slices; keep answer-bearing maps only in this class.

3. **Startup: full DOM per file** (`BankStore.java:244-263,250`) — `readTree` builds 831 MB of trees before extracting 8 header fields. Measured real `BankStore.loadAll`: **2.7 s warm / ~6 s cold**, retained heap **261 MB**. **Measured.** Fix: stream `questions` with Jackson `JsonFactory` and keep columnar arrays + interning (already correct); no semantics change.

4. **O(N) request scans in BankStore** — `search:297-315`, `headerById:320-327`, `coverage:332-360`, `volume:365-372` each scan 329,652 entries; `headerById` is a linear lookup on every `GET /question/{id}`. **Confirmed by inspection.** Fix: preload `Map<String,Integer>` id→index and per-subject index at construction.

5. **Per-request config JSON re-parsing** — `AnalyticsService.questionMeta:302-321` and `TeachNextService.topicOf:199-212` call `Json.readTree` on every question's config each request. **Confirmed.** Fix: parse once into the existing metadata map.

Smallest safe path: fixes 1–2 share one indexed `BankRecordSource`; fix 3–4 need no store-semantics change. All answer-free-store tests (`BankStoreTest`) remain valid.
