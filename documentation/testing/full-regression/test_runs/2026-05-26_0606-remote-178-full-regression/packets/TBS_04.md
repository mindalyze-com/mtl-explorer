# Packet: TBS_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TBS_04
- In scope: quick-view or preset subset buttons in the track browser.
- Out of scope: sort presets, covered by TBS_03.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_01, TBS_03.
- Required app/data state: Stats Tracks tab open with 10 tracks.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: switch visible track-browser controls if present.
- Not allowed: edit track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_04 | Scanned the Tracks tab controls after search/sort testing. | Quick-view/preset buttons switch the browser subset correctly and preserve usable sorting/search behavior. | The tested Tracks tab exposed search, sort controls, table columns, summary, and pagination, but no quick-view/preset subset buttons were visible. | FAIL | `assets/TBS_01-track-browser-list.webp`, `assets/TBS_03-walking-summary.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TBS-01 | P3 | Track browser does not expose quick-view/preset subset buttons. | Open Stats > Tracks and scan controls around search/sort/table. | User can switch track-browser subsets through quick-view/preset controls. | Only search, sort controls, table, summary, and pagination are visible. | `assets/TBS_01-track-browser-list.webp` | Users cannot quickly switch predefined track-browser subsets from the tested surface. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/TBS_01-track-browser-list.webp | Tracks tab control surface. |
| assets/TBS_03-walking-summary.webp | Search/sort area showing no quick-view subset buttons. |

## Timings

| Step | Timing |
|---|---:|
| Quick-view control scan | <1m |

## Handoff Notes

- Completed: TBS_04 terminal FAIL due missing visible quick-view/preset subset controls.
- Remaining unfinished coverage: continue with TBS_05.
- Blocked or not applicable: none.
- State left for the next packet: Tracks tab open, search cleared.
