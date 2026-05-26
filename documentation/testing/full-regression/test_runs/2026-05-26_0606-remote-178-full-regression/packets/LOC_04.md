# Packet: LOC_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: LOC_04
- In scope: Boundary values such as zero ascent/descent and no-elevation data rendering in user-visible UI.
- Out of scope: Fixing locale decimal mismatch found in LOC_02.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_03
- Required app/data state: Signed in; upload endpoint available.
- Required browser context: Desktop browser plus authenticated API token for disposable upload.

## Allowed Mutations

- Allowed: Upload disposable GPX files for boundary-value coverage and refresh client data.
- Not allowed: Modify non-regression source data outside disposable uploads.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_04 | Uploaded `LOC_04-boundary.gpx` and `LOC_04-null-elevation.gpx`, refreshed to 14 tracks, and inspected Stats recent activity and API import stats. | Boundary values render sensibly, not as `NaN` or blank. | Zero ascent/descent and no-elevation imports rendered as normal recent activity rows with dates, distances, and durations. Visible body text did not contain `NaN`, `undefined`, `null`, or `Infinity`. | PASS | `assets/LOC_04-boundary-values.txt`, `assets/LOC_04-boundary.gpx`, `assets/LOC_04-null-elevation.gpx` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/LOC_04-boundary-values.txt | Upload/API/UI observations for boundary-value tracks. |
| assets/LOC_04-boundary.gpx | Disposable descending/zero-ascent boundary GPX. |
| assets/LOC_04-null-elevation.gpx | Disposable no-elevation GPX. |

## Timings

| Step | Timing |
|---|---:|
| Upload, refresh, and inspect boundary tracks | <5 min |

## Handoff Notes

- Completed: LOC_04 passed.
- Remaining unfinished coverage: MOB_01 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Signed in, en-GB restored, hard reload cleared the freshness banner, 14 tracks visible.
