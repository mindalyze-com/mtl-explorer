# Packet: DEL_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DEL_02
- In scope: Automatic delete processing and settled indexer state.
- Out of scope: Cross-view UI absence checks.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_01.
- Required app/data state: Exactly two watched sources removed.
- Required browser context: Not required unless manual Rescan GPS is needed.

## Allowed Mutations

- Allowed: Wait for automatic processing; trigger Rescan GPS only if needed.
- Not allowed: Restore a deletion target or remove another source.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_02 | Waited for live deletion processing, inspected both indexed-file mappings, their former track joins, retained joins, and the complete indexer status counts. | Both sources reach a removed state and no unexpected processing remains; Rescan GPS is used only if automatic processing fails. | Both targets reached REMOVED within 44 ms of each other and have no gps_track join. Retained sources remain COMPLETED_WITH_SUCCESS. Only completed, excluded, and removed status groups exist; no manual rescan was needed. | PASS | [assets/DEL_02-delete-processing.txt](../assets/DEL_02-delete-processing.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_02-delete-processing.txt](../assets/DEL_02-delete-processing.txt) | Exact REMOVED timestamps, absent track joins, retained mappings, settled status counts, and rescan result. |

## Screenshot Evidence

Not applicable; the downstream user-visible state is captured in DEL_03.

## Timings

| Step | Timing |
|---|---:|
| Target removal-state timestamps | 2026-08-18 04:22:31.421-04:22:31.465 UTC |

## Handoff Notes

- Completed: Automatic processing for both deleted sources and settled-state verification.
- Remaining unfinished coverage: None for DEL_02.
- Blocked or not applicable: None; Rescan GPS was not needed.
- State left for the next packet: Both targets indexed REMOVED; no associated track row.
