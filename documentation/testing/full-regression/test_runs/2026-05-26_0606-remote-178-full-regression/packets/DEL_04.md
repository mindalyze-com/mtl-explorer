# Packet: DEL_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_04
- In scope: delete-two-track flow for imported GPX source files.
- Out of scope: FIT import and non-deleted track format coverage.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01-IMP_09.
- Required app/data state: five GPX tracks imported before deletion.
- Required browser context: desktop signed-in browser.

## Allowed Mutations

- Allowed: delete selected GPX source files from `./data/gpx`, wait for watcher, refresh UI.
- Not allowed: delete unrelated files or perform Docker cleanup.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_04 | Opened a remaining Jura track from the map after deletion. | Remaining imported tracks still display and open correctly. | Jura #100001 opened from the map and rendered its overview/details after deletion. | PASS | `assets/DEL_04-remaining-jura-opens.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/DEL_04-remaining-jura-opens.webp | Remaining GPX detail after delete. |

## Timings

| Step | Timing |
|---|---:|
| Delete flow step | <1m |

## Handoff Notes

- Completed: DEL_04 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none.
- State left for the next packet: three GPX tracks remain (Jura, Mosel, Lannion); FIT not imported yet.
