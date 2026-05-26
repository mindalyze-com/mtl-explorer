# Packet: TRD_12

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_12
- In scope: statistics exclusion/re-include toggle in track details.
- Out of scope: direct API patching.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01.
- Required app/data state: track #100001 details open.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: toggle exclusion and restore inclusion through visible UI if available.
- Not allowed: direct API/database changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_12 | Scanned Overview and Quality detail tabs for an exclude/include statistics toggle. | Exclude from statistics stops the track counting; re-include restores it. | No visible exclusion toggle was exposed in the tested detail UI, so the exclude/re-include workflow could not be performed from the frontend. | FAIL | `assets/TRD_12-exclusion-control-scan.webp`, `assets/TRD_12-quality-exclusion-scan.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TRD-04 | P2 | Track details do not expose a visible statistics exclusion toggle. | Open track #100001 Overview/Quality tabs and search for exclude/include statistics controls. | User can exclude and re-include a track from statistics. | No visible control was present. | `assets/TRD_12-exclusion-control-scan.webp`, `assets/TRD_12-quality-exclusion-scan.webp` | Users cannot manage stat inclusion from the tested details UI. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_12-exclusion-control-scan.webp | Overview scan for exclusion controls. |
| assets/TRD_12-quality-exclusion-scan.webp | Quality tab scan for exclusion controls. |

## Timings

| Step | Timing |
|---|---:|
| Exclusion control scan | <2m |

## Handoff Notes

- Completed: TRD_12 terminal FAIL.
- Remaining unfinished coverage: continue with TRD_13.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
