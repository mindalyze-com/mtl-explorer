# Packet: DAT_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_08
- In scope: Standard fully synthetic GPS/camera-time photos and matching GPX activity.
- Out of scope: Watched-folder import and media indexing (MED_06).

## Prerequisites

- Required previous coverage IDs or run packets: DAT_07 and RUN_SETUP.
- Required app/data state: Packaged `/app/demo/generate_regression_photos.py` available.
- Required browser context: None.

## Allowed Mutations

- Allowed: Generate fixtures under mounted `data/logs/` outside watched trees.
- Not allowed: Copy JPEG fixtures into repository artifacts or watched media before MED_06.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_08 | Ran the packaged generator inside the required beta app container and saved its manifest while confirming `data/gpx/` and `data/media/` remained empty. | At least four fully synthetic private-data-free GPS photos are available, plus camera-time photos and a matching timestamped activity. | The generator created four GPS JPEGs, two camera-time-only JPEGs, and a six-point/six-timestamp GPX activity. The 2,605-byte manifest records names, sizes, hashes, coordinates, times, and expected origins. | PASS | [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Required synthetic media/activity manifest. |

## Screenshot Evidence

Not applicable; generated fixtures remain outside watched folders.

## Timings

| Step | Timing |
|---|---:|
| Packaged fixture generation and manifest copy | 3.0 s |

## Handoff Notes

- Completed: Complete standard synthetic media/activity set generated and manifest preserved.
- Remaining unfinished coverage: None for DAT_08.
- Blocked or not applicable: None.
- State left for the next packet: Fixtures are under `data/logs/2026-08-17_2236-beta-full-regression-media-fixtures/`; watched GPS/media folders remain empty.
