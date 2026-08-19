# Packet: TRD_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_01
- In scope: GPX- and FIT-backed details opened from user-facing navigation with IDs/source filenames.
- Out of scope: Direct detail URLs as the only navigation evidence.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_15, IMP_06, and FIT_02.
- Required app/data state: Imported public GPX and original FIT retained.
- Required browser context: Preserved Filter/Statistics navigation evidence.

## Allowed Mutations

- Allowed: Reuse the already completed user-facing selection evidence.
- Not allowed: Replace the original FIT with a converted GPX.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_01 | Reconciled the preserved Filter Review and Statistics Tracks selections with source manifests and detail identities. | At least one GPX and one FIT track open from user-facing navigation; IDs/filenames recorded. | Filter Review opened GPX `Lannion_Plestin_parcours24.4RE.gpx` as 100004. Statistics Tracks opened `Activity.fit` as 100005. Both detail views and mini-maps loaded. | PASS | [assets/TRD_01-source-navigation.txt](../assets/TRD_01-source-navigation.txt), [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt), [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_01-source-navigation.txt](../assets/TRD_01-source-navigation.txt) | Required source-type navigation mapping. |
| [assets/IMP_06-per-file.txt](../assets/IMP_06-per-file.txt) | Original GPX user-facing selection mapping. |
| [assets/FIT_02-index-display.txt](../assets/FIT_02-index-display.txt) | Original FIT Statistics navigation and detail evidence. |

## Screenshot Evidence

Unavailable under ACC_04; routes, IDs, names, source files, and semantic mini-map evidence are preserved.

## Timings

| Step | Timing |
|---|---:|
| Original five GPX exact-name selections | About 35 s |
| Original FIT search/open | About 14 s |

## Handoff Notes

- Completed: GPX/FIT user-facing track detail sources and identities.
- Remaining unfinished coverage: None for TRD_01.
- Blocked or not applicable: None.
- State left for the next packet: Local map/source defaults restored; details can be reopened from Statistics or Filter.
