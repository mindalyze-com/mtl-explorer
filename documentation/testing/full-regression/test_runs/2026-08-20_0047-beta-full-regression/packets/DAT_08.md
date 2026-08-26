# Packet: DAT_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_08
- In scope: Generate the standard fully synthetic media/activity set, retain the exact manifest, and later copy all required files into their watched run folders.
- Out of scope: Indexing media before MED_06 or changing the clean IMP_01 baseline.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_07 and MED_06.
- Required app/data state: Required image running; generated files indexed from watched folders.
- Required browser context: Admin at MED_06.

## Allowed Mutations

- Allowed: Run the packaged generator into `data/logs`; copy the manifest to run assets; later copy exact GPX/media files in MED_06.
- Not allowed: Use private media, copy fixture binaries into the repository, or place media into watched folders before MED_06.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_08 | Run packaged generator; enumerate output; preserve exact manifest; then place GPX/media in watched folders during MED_06. | Six JPEGs, two videos, matching six-point GPX, complete checksums/ffprobe manifest, and exact watched-folder placement. | Generator produced all nine files in 35 s. Exact GPX and eight media files were copied in MED_06; checksums match, track 100013 indexed, and MEDIA completed 8/8. | PASS | [assets/DAT_08-generation.txt](../assets/DAT_08-generation.txt); [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json); [assets/MED_06-setup.txt](../assets/MED_06-setup.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_08-generation.txt](../assets/DAT_08-generation.txt) | Generator command outcome, timing, inventory, and ordering note. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Exact synthetic media manifest with checksums and ffprobe data. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; exact Admin/index state is recorded in MED_06 evidence.

## Timings

| Step | Timing |
|---|---:|
| Synthetic fixture generation | 35 s |
| Watched placement and index verification | 4 min |

## Handoff Notes

- Completed: Generation, manifest preservation, exact watched placement, GPX-first ordering, and 8/8 media indexing.
- Remaining unfinished coverage: None for DAT_08.
- Blocked or not applicable: None.
- State left for the next packet: Generated source and manifest remain in `data/logs`; watched copies are indexed.
