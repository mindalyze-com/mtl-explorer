# Packet: IMP_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: IMP_03
- In scope: Wait for GPX indexing to finish and determine whether manual Rescan GPS was needed.
- Out of scope: Post-import UI count/detail verification; covered by IMP_04+.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_02.
- Required app/data state: Five GPX files present in watched import folder.
- Required browser context: Existing logged-in browser may poll status but is not required for this packet.

## Allowed Mutations

- Allowed: Wait/read app logs and status.
- Not allowed: Trigger Rescan GPS unless live watching fails; add/delete files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| IMP_03 | Waited after watched-folder copy and reviewed app logs for live watcher and ingest completion. | Live watcher or documented Rescan GPS processes the five GPX files and indexing finishes. | Live watcher detected CREATE events for all five GPX files at `15:39:24Z`; each file completed with `status=SUCCESS` by `15:39:38Z`; duplicate finder settled for 5 tracks at `15:39:56Z`; Rescan GPS was not needed. | PASS | [assets/IMP_03-index-wait-logs.txt](../assets/IMP_03-index-wait-logs.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/IMP_03-index-wait-logs.txt](../assets/IMP_03-index-wait-logs.txt) | Cropped live watcher, ingest success, duplicate finder, and compose status logs. |

## Timings

| Step | Timing |
|---|---:|
| Live watcher detection after file copy | ~106 seconds from copy evidence to first CREATE log |
| Ingest completion after watcher detection | ~14 seconds to final `status=SUCCESS` line |
| Duplicate finder settle after ingest | ~18 seconds after final ingest success |

## Handoff Notes

- Completed: IMP_03 terminal as `PASS`; no manual Rescan GPS was needed.
- Remaining unfinished coverage: Continue with `IMP_04` upload/index status and freshness/jobs verification.
- Blocked or not applicable: None.
- State left for the next packet: Five GPX tracks are indexed; browser may still need a freshness reload to show updated data.
