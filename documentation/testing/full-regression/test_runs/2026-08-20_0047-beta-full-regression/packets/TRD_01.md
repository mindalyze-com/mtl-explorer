# Packet: TRD_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_01
- In scope: Open and record one GPX-backed and one FIT-backed track from user-facing navigation.
- Out of scope: Detailed tab behavior, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_15, IMP_06-IMP_07, and FIT_02.
- Required app/data state: GPX track 100000 and FIT track 100005 retained.
- Required browser context: Same-run signed-in map/statistics browser.

## Allowed Mutations

- Allowed: Reuse durable same-run navigation and identity evidence.
- Not allowed: Infer filenames only from backend records without UI-open evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_01 | Map/filter-open GPX 100000 and Stats Tracks-open FIT 100005; record source identities. | Both source types open through user-facing navigation with IDs/filenames recorded. | GPX `JuraRoute72011.gpx` opened as 100000; FIT `Activity.fit` opened as 100005. | PASS | [assets/TRD_01-source-identities.txt](../assets/TRD_01-source-identities.txt); [assets/IMP_07-map-interaction.txt](../assets/IMP_07-map-interaction.txt); [assets/FIT_02-index-ui.txt](../assets/FIT_02-index-ui.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_01-source-identities.txt](../assets/TRD_01-source-identities.txt) | Required IDs, filenames, names, and user-facing paths. |
| [assets/DAT_03-source-manifest.txt](../assets/DAT_03-source-manifest.txt) | GPX imported identity and source filename. |
| [assets/FIT_02-index-ui.txt](../assets/FIT_02-index-ui.txt) | FIT source filename and Statistics row/detail open. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; durable same-run UI navigation evidence is linked above.

## Timings

| Step | Timing |
|---|---:|
| Identity mapping from completed opens | <1 min |

## Handoff Notes

- Completed: Required GPX/FIT user-facing opens and source identity record.
- Remaining unfinished coverage: None for TRD_01.
- Blocked or not applicable: None.
- State left for the next packet: Original healthy app; map settings reset; both tracks retained.
