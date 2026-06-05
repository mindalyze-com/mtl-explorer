# Packet: MAP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_03
- In scope: Newly imported tracks appear without a full browser restart after accepting the freshness/reload prompt.
- Out of scope: Other coverage IDs except where noted as shared state/evidence.

## Prerequisites

- Required previous coverage IDs or run packets: Required GPX import flow completed earlier in this run.
- Required app/data state: Current run-state and packet set for this run.
- Required browser context: As specified by the coverage action.

## Allowed Mutations

- Allowed: Use prior direct import/reload evidence and update packet/run-state.
- Not allowed: Collapse this ID into a parent row or skip required direct evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_03 | Reviewed the post-GPX-import helper reload and map/browser/filter/stat evidence captured immediately after import. | Newly imported tracks appear after accepting the freshness/reload prompt, without restarting the browser. | After five GPX files were imported, the in-app helper reload was used and the same browser flow showed 5 tracks on map, stats, tracks, and filter surfaces; no browser restart was required. | PASS | [assets/IMP_05-reload-helper.webp](../assets/IMP_05-reload-helper.webp); [assets/IMP_05-reload-helper.txt](../assets/IMP_05-reload-helper.txt); [assets/IMP_05-map-after-reload.webp](../assets/IMP_05-map-after-reload.webp); [assets/IMP_05-map-after-reload.txt](../assets/IMP_05-map-after-reload.txt); [assets/IMP_05-ui-api-summary.txt](../assets/IMP_05-ui-api-summary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|


## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_05-reload-helper.webp](../assets/IMP_05-reload-helper.webp) | Screenshot evidence |
| [assets/IMP_05-reload-helper.txt](../assets/IMP_05-reload-helper.txt) | Text/log evidence |
| [assets/IMP_05-map-after-reload.webp](../assets/IMP_05-map-after-reload.webp) | Screenshot evidence |
| [assets/IMP_05-map-after-reload.txt](../assets/IMP_05-map-after-reload.txt) | Text/log evidence |
| [assets/IMP_05-ui-api-summary.txt](../assets/IMP_05-ui-api-summary.txt) | Text/log evidence |

## Screenshot Evidence

![assets/IMP_05-reload-helper.webp](../assets/IMP_05-reload-helper.webp)
![assets/IMP_05-map-after-reload.webp](../assets/IMP_05-map-after-reload.webp)

## Timings

| Step | Timing |
|---|---:|
| Evidence review | <1 minute |

## Handoff Notes

- Completed: This coverage ID is terminal.
- Remaining unfinished coverage: Continue with the next queue row.
- Blocked or not applicable: none.
- State left for the next packet: unchanged.
