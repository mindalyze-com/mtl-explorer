# Packet: MED_39

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_39
- In scope: MP4 embedded and MOV interpolated provenance, manual MOV location/note precedence, clear restoration, manifest comparison, and read-only database evidence.
- Out of scope: Reload/restart persistence covered by MED_40.

## Prerequisites

- Required previous coverage IDs or run packets: MED_36-38 generated video verification.
- Required app/data state: Eight-item baseline; no retained manual location.
- Required browser context: Track 100028 Media tab and shared viewer.

## Allowed Mutations

- Allowed: One disposable manual MOV location/note followed by exact clear.
- Not allowed: Original EXIF point, track correlation, source file, or manifest mutation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_39 | Compared manifest/UI/database video provenance, assigned a manual MOV point and note, inspected precedence/separate evidence, cleared it, and rechecked both video viewers and database rows. | MP4 remains EXIF_EMBEDDED; MOV is TRACK_INTERPOLATED/Estimated; USER_ASSIGNED wins without overwriting track evidence; clear restores the exact persisted interpolation. | MP4 stayed Video GPS/EXIF_EMBEDDED at its manifest point. MOV moved from Estimated/TRACK_INTERPOLATED to Set by you/USER_ASSIGNED while its selected route point remained separate and unchanged, then clear removed the manual row and restored the same interpolated point/UI. | PASS | [assets/MED_39-video-provenance.txt](../assets/MED_39-video-provenance.txt); [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_39-video-provenance.txt](../assets/MED_39-video-provenance.txt) | Exact manifest/UI/database baseline, manual assignment, precedence, clear, and cleanup evidence. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Frozen generated-video coordinates and expected origins. |

## Screenshot Evidence

Live desktop screenshot inspection accompanied the Estimated, Video GPS, Set by you, and restored viewer/map states. ACC_04 prevents durable local screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Manual assignment UI update | Under 450 ms |
| Clear and restoration UI update | Under 200 ms |

## Handoff Notes

- Completed: End-to-end provenance comparison, manual location/note precedence, separate-evidence check, exact clear, and cleanup verification.
- Remaining unfinished coverage: None for MED_39.
- Blocked or not applicable: Durable screenshot saving remains unavailable under ACC_04.
- State left for the next packet: MP4 viewer open; no manual rows; MOV restored to TRACK_INTERPOLATED; MP4 remains EXIF_EMBEDDED.
