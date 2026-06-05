# Packet: MAP_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_04
- In scope: Deleted tracks disappear from all map sources, selection lists, and popups.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Deletion flow DEL_01 through DEL_04 terminal.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Use prior deletion-surface evidence and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_04 | Reviewed the deletion-surface packet covering map, browser, filter, selection, heatmap, related, statistics, and remaining-track detail surfaces. | Deleted tracks from the required data-change flow disappear from map sources, selection lists, popups, and related surfaces. | After deleting Lannion and VoieVerte source files, current surfaces showed three remaining GPX tracks before FIT/format imports; deleted filenames/names were absent from map, browser searches, filter, selection, heatmap, related list, and stats. Subsequent current map total reflects later valid imports without deleted tracks returning. | PASS | [assets/DEL_03-deletion-surface-summary.txt](../assets/DEL_03-deletion-surface-summary.txt); [assets/DEL_03-map-after-deletion.webp](../assets/DEL_03-map-after-deletion.webp); [assets/DEL_03-search-deleted-lannion.webp](../assets/DEL_03-search-deleted-lannion.webp); [assets/DEL_03-search-deleted-voieverte.webp](../assets/DEL_03-search-deleted-voieverte.webp); [assets/DEL_03-selection-list-after-deletion.webp](../assets/DEL_03-selection-list-after-deletion.webp); [assets/DEL_03-related-after-deletion.webp](../assets/DEL_03-related-after-deletion.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_03-deletion-surface-summary.txt](../assets/DEL_03-deletion-surface-summary.txt) | Text/log evidence |
| [assets/DEL_03-map-after-deletion.webp](../assets/DEL_03-map-after-deletion.webp) | Screenshot evidence |
| [assets/DEL_03-search-deleted-lannion.webp](../assets/DEL_03-search-deleted-lannion.webp) | Screenshot evidence |
| [assets/DEL_03-search-deleted-voieverte.webp](../assets/DEL_03-search-deleted-voieverte.webp) | Screenshot evidence |
| [assets/DEL_03-selection-list-after-deletion.webp](../assets/DEL_03-selection-list-after-deletion.webp) | Screenshot evidence |
| [assets/DEL_03-related-after-deletion.webp](../assets/DEL_03-related-after-deletion.webp) | Screenshot evidence |

## Screenshot Evidence

![assets/DEL_03-map-after-deletion.webp](../assets/DEL_03-map-after-deletion.webp)
![assets/DEL_03-search-deleted-lannion.webp](../assets/DEL_03-search-deleted-lannion.webp)
![assets/DEL_03-search-deleted-voieverte.webp](../assets/DEL_03-search-deleted-voieverte.webp)
![assets/DEL_03-selection-list-after-deletion.webp](../assets/DEL_03-selection-list-after-deletion.webp)
![assets/DEL_03-related-after-deletion.webp](../assets/DEL_03-related-after-deletion.webp)

## Timings

| Step | Timing |
|---|---:|
| Evidence review | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
