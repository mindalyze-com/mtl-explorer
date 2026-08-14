# Packet: TRD_15

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_15.
- In scope: Track Details origin return, list state, browser history, direct-link close, and narrow repeat.
- Out of scope: general mobile layout, covered by later mobile packets.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_14.
- Required app/data state: #100005 visible in both Statistics Tracks and Filter Review.
- Required browser context: signed-in desktop in-app browser; direct URL and sheet origins.

## Allowed Mutations

- Allowed: filter list searches, open/close details, and browser Back/Forward.
- Not allowed: alter track records.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_15 | Tested direct close, Statistics Tracks and Filter Review origins, normal Close, and Back/Forward. | Each origin and list state are preserved; narrow repeat behaves the same; direct detail closes to map. | Direct close passed. Filter Review preserved its one-result search through Close and Back, and Forward restored details. Statistics returned to the right sheet/tab but lost its one-result search through both Close and Back, expanding 2→13 visible rows. The browser surface had no narrow viewport control after the desktop failure was terminal. | FAIL | [origin/history log](../assets/TRD_15-origin-history.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TRD-15-P2 | P2 | Statistics Track Details loses originating search state. | Statistics > Tracks; search Activity.fit; open #100005; Close or Back. | Return to the same one-result Tracks list. | Tracks tab returns with all 12 rows; search/result state is cleared. | [origin/history log](../assets/TRD_15-origin-history.txt) | Users lose their place and must repeat the search after inspecting a track. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_15-origin-history.txt](../assets/TRD_15-origin-history.txt) | Per-origin row counts, URLs, history behavior, direct close, and viewport limitation. |

## Screenshot Evidence

Exact URL and visible-row transitions prove the state loss more directly than a static screenshot.

## Timings

| Step | Timing |
|---|---:|
| Each open/return transition | < 1 s |
| Full desktop origin/history audit | < 5 min |

## Handoff Notes

- Completed: TRD_15 is terminal `FAIL` with TRD-15-P2.
- Remaining unfinished coverage: FLT_01 onward.
- Blocked or not applicable: narrow repeat could not be executed in the selected in-app browser, but the coverage ID is terminal because the required desktop behavior reproducibly fails.
- State left for the next packet: Filter > Review tracks open with Track 100005 search preserved and the all-tracks filter active.
