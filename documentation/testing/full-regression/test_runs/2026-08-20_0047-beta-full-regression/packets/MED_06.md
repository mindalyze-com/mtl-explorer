# Packet: MED_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_06
- In scope: Capture media baseline, import/index the matching GPX first, copy exactly eight media fixtures, run visible Rescan Media, and verify completion/freshness.
- Out of scope: Detailed media map/detail behavior, covered by MED_01 onward.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08 and FMT_02.
- Required app/data state: Generated media set remains outside watched trees; MEDIA count zero.
- Required browser context: Signed-in Admin Processing, Maintenance, and Data status.

## Allowed Mutations

- Allowed: Copy exact matching GPX/media files; run visible Rescan Media; apply freshness Reload.
- Not allowed: Alter fixture binaries/manifest or delete the two deletion fixtures before MED_40.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| MED_06 | Capture zero baseline; index matching GPX; copy eight manifest files; run Admin Rescan Media; verify index/correlation and reload freshness. | GPX indexes first, then all eight media complete with changed media freshness and no failures. | Track 100013 indexed SUCCESS. Rescan processed 8/8 in 228 ms, correlation processed all eight, Admin shows 8/8 done, and media revision moved 0 to 9 before synchronized reload. | PASS | [assets/MED_06-setup.txt](../assets/MED_06-setup.txt); [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_06-setup.txt](../assets/MED_06-setup.txt) | Baseline, GPX import, eight-file rescan/correlation, and freshness evidence. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Exact fixture checksums, GPS/time metadata, and ffprobe data. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact visible Admin state and logs are recorded in linked evidence.

## Timings

| Step | Timing |
|---|---:|
| Matching GPX ingest | 73 ms |
| Media rescan | 228 ms |
| Media correlation | 28 ms |
| Full baseline/copy/Admin/reload flow | 4 min |

## Handoff Notes

- Completed: Required media dataset is indexed and browser/server freshness is synchronized.
- Remaining unfinished coverage: None for MED_06.
- Blocked or not applicable: None.
- State left for the next packet: 14 GPS tracks indexed, eight media indexed, deletion fixtures retained, Data status in sync.
