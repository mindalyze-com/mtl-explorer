# Packet: ADM_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_02
- In scope: Track file upload availability, accepted formats, valid upload,
  progress/state transition, success, unsupported format, and empty file.
- Out of scope: Detailed indexer counters, covered by ADM_03.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_01.
- Required app/data state: Healthy signed-in quick install and synthetic files.
- Required browser context: Desktop Admin > Import & sync.

## Allowed Mutations

- Allowed: Upload one disposable synthetic GPX into GPX-UPLOAD.
- Not allowed: Upload private data or bypass frontend validation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_02 | Inspected availability/formats; picked and uploaded a valid 1,088-byte synthetic GPX; then picked an unsupported text file and an empty GPX. | Availability, formats, progress/state, success, unsupported-format error, and empty-file error are clear. | Nine accepted extensions and GPX-UPLOAD help were clear. Valid selection exposed filename/size and Upload, then succeeded and indexed as track 100023. Unsupported and empty selections were rejected with specific messages before upload. | PASS | [assets/ADM_02-upload.txt](../assets/ADM_02-upload.txt); [assets/ADM_02-empty-file-error.jpg](../assets/ADM_02-empty-file-error.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_02-upload.txt](../assets/ADM_02-upload.txt) | Exact picker, validation, success, watched-file, and database results. |
| [assets/ADM_02-empty-file-error.jpg](../assets/ADM_02-empty-file-error.jpg) | Visible zero-byte-file validation and freshness banner. |
| [assets/MED_19-overlap-activity-b.gpx](../assets/MED_19-overlap-activity-b.gpx) | Synthetic valid upload source. |
| [assets/ADM_02-unsupported.txt](../assets/ADM_02-unsupported.txt) | Synthetic unsupported-format source. |
| [assets/ADM_02-empty.gpx](../assets/ADM_02-empty.gpx) | Synthetic zero-byte GPX source. |

## Screenshot Evidence

- The clipped Admin panel image preserves the exact empty-file error and the
  New data available banner produced by the valid upload.

## Timings

| Step | Timing |
|---|---:|
| Valid file selection | Under 500 ms |
| Upload response | Under 1 s |
| Terminal indexing verification | About 5 s after upload |
| Unsupported/empty validation | Under 500 ms each |

## Handoff Notes

- Completed: Track file upload and both negative picker cases passed.
- Remaining unfinished coverage: None for ADM_02.
- Blocked or not applicable: Numeric transfer progress was too brief to retain;
  the observable state transition and terminal indexing passed.
- State left for the next packet: Track 100023 is indexed; browser intentionally
  remains stale with Processing Live and New data available visible for ADM_03.
