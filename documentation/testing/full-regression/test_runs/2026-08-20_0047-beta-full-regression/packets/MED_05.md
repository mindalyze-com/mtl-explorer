# Packet: MED_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_05
- In scope: Missing-photo behavior and recovery controls in the media viewer.
- Out of scope: Successful HEIC conversion, covered by MED_04.

## Prerequisites

- Required previous coverage IDs or run packets: MED_04.
- Required app/data state: Original eight-media baseline plus one disposable indexed JPEG whose source can be moved outside the watched folder.
- Required browser context: Admin media rescan, Stats Trends Undated media, and the media viewer.

## Allowed Mutations

- Allowed: Add, index, move, view, retry, and remove one disposable synthetic JPEG; remove the temporary MED_04 HEIC row.
- Not allowed: Modify or derive a fixture from private media.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_05 | Index a fresh disposable JPEG, move its source outside the watched folder before first display, open it from Undated media, use Retry, and rescan after removal. | A missing/broken photo shows a recoverable error instead of a blank sheet. | The viewer showed `Preview unavailable` and `Failed to fetch image: 500`, retained Retry, Download, and file details, and repeated the explicit error after Retry. Cleanup restored eight active media files. | PASS | [assets/MED_05-broken-recovery.txt](../assets/MED_05-broken-recovery.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_05-broken-recovery.txt](../assets/MED_05-broken-recovery.txt) | Fixture identity, rescan logs, viewer recovery state, Retry result, and cleanup verification. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the accessible viewer state, controls, file details, and server rescan results are linked above.

## Timings

| Step | Timing |
|---|---:|
| Generate, transfer, and index disposable JPEG | 2 min |
| Missing-source viewer and Retry validation | 2 min |
| Remove row and verify eight-media baseline | 1 min |

## Handoff Notes

- Completed: Missing-source error, recovery controls, retry behavior, and watched-folder cleanup.
- Remaining unfinished coverage: None for MED_05.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04 and does not block this accessible-state result.
- State left for the next packet: Original eight media files are active; disposable HEIC/JPEG backups remain outside the watched folder for final cleanup; Admin Processing is open.
