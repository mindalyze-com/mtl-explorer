# Packet: FLT_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FLT_08
- In scope: Clearing/disabling the active filter restores all tracks.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Previous queue rows through TRD_14 terminal; current dataset has 11 tracks.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: UI filter interactions, local browser storage changes for filter settings, screenshot/text evidence, packet/run-state updates.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_08 | Applied Activities by keyword with keyword=format to reduce the map to 3 / 11 tracks, then disabled filtering from the filter header and checked the map card and stored config. | Clearing the active filter restores all tracks. | Before clearing, the map showed 3 / 11 tracks; after disabling filtering, the off panel appeared, the map card showed 11 Tracks, and storage reset to the default Smart Base filter with empty params. | PASS | [assets/FLT_08-before-clear-active-filter.webp](../assets/FLT_08-before-clear-active-filter.webp); [assets/FLT_08-filter-disabled-all-tracks.webp](../assets/FLT_08-filter-disabled-all-tracks.webp); [assets/FLT_08-clear-restores-all-tracks.txt](../assets/FLT_08-clear-restores-all-tracks.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_08-before-clear-active-filter.webp](../assets/FLT_08-before-clear-active-filter.webp) | Screenshot evidence |
| [assets/FLT_08-filter-disabled-all-tracks.webp](../assets/FLT_08-filter-disabled-all-tracks.webp) | Screenshot evidence |
| [assets/FLT_08-clear-restores-all-tracks.txt](../assets/FLT_08-clear-restores-all-tracks.txt) | Text/log evidence |

## Screenshot Evidence

![assets/FLT_08-before-clear-active-filter.webp](../assets/FLT_08-before-clear-active-filter.webp)
![assets/FLT_08-filter-disabled-all-tracks.webp](../assets/FLT_08-filter-disabled-all-tracks.webp)

## Timings

| Step | Timing |
|---|---:|
| Browser automation and evidence capture | ~5 minutes |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
