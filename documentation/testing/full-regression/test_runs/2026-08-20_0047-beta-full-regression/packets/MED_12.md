# Packet: MED_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_12
- In scope: Read-only database proof of retained REMOVED index rows, DELETE snapshots, and no active media rows for the two synthetic deletion fixtures.
- Out of scope: Any database mutation or non-synthetic data.

## Prerequisites

- Required previous coverage IDs or run packets: MED_09-11 settled delete and UI/cache validation.
- Required app/data state: Six active media; both deleted sources absent from watched storage.
- Required browser context: None; coverage explicitly prescribes read-only database inspection.

## Allowed Mutations

- Allowed: Read-only SQL limited to the exact two synthetic filenames/IDs.
- Not allowed: SQL writes or inspection/reporting of non-synthetic media.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_12 | Queried exact indexed_file, media_file_audit, and active media_file rows for delete-a/delete-b only. | Each index row is REMOVED, each has a DELETE snapshot, and neither has an active media row. | Rows 300018/300021 are REMOVED; audit holds DELETE snapshots for media 400004/400007 with exact synthetic coordinates/dimensions; active counts are 0/0. | PASS | [assets/MED_12-delete-audit.txt](../assets/MED_12-delete-audit.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_12-delete-audit.txt](../assets/MED_12-delete-audit.txt) | Exact synthetic index/audit/active-row evidence. |

## Screenshot Evidence

Not applicable to read-only database inspection.

## Timings

| Step | Timing |
|---|---:|
| Three exact read-only queries | Under 1 s each |

## Handoff Notes

- Completed: REMOVED/index retention, DELETE snapshots, and active-row absence for both exact fixtures.
- Remaining unfinished coverage: None for MED_12.
- Blocked or not applicable: None.
- State left for the next packet: Delete-two-photo flow complete; six active media remain; recoverable backups stay outside watched storage for final run cleanup.
