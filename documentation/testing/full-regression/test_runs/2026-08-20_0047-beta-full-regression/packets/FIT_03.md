# Packet: FIT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FIT_03
- In scope: Verify FIT-backed overview, graphs, quality, events, related tracks, mini-map, and point/selection behavior match the GPX detail flow.
- Out of scope: File downloads, covered by FIT_04-FIT_05.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_02.
- Required app/data state: FIT-backed track 100005 indexed successfully.
- Required browser context: Track 100005 detail sheet.

## Allowed Mutations

- Allowed: Switch detail tabs, use chart controls, and click the mini-map line.
- Not allowed: Edit curation fields or track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FIT_03 | Inspect all required detail tabs, mini-map, populated charts, related state, event state, and map-line interaction. | FIT-backed detail components render as for GPX-backed tracks. | Overview and maps render; six chart families contain 345-350 points; Quality is SUCCESS/UNIQUE with 3,600 points; five related tracks and explicit empty Events state render; map-line interaction causes no error. | PASS | [assets/FIT_03-details.txt](../assets/FIT_03-details.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIT_03-details.txt](../assets/FIT_03-details.txt) | Per-tab, chart, quality, related, event, and mini-map evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible chart and UI state is recorded in the linked asset.

## Timings

| Step | Timing |
|---|---:|
| FIT detail-tab and mini-map checks | 5 min |

## Handoff Notes

- Completed: Required FIT detail parity checks.
- Remaining unfinished coverage: None for FIT_03.
- Blocked or not applicable: None.
- State left for the next packet: Track 100005 Events tab is open; data is unchanged.
