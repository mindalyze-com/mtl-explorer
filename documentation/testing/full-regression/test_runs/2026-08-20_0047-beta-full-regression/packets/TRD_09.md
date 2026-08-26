# Packet: TRD_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_09
- In scope: GPX download from a non-GPX source track.
- Out of scope: Original-source download.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_05 and TRD_08.
- Required app/data state: FIT-backed track 100005 retained.
- Required browser context: Same-run visible FIT Overview conversion download.

## Allowed Mutations

- Allowed: Reuse the direct same-run FIT_05 payload validation.
- Not allowed: Repair or synthesize the downloaded XML.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_09 | Evaluate the same-run visible Download GPX action for FIT track 100005. | A valid GPX downloads despite the FIT source format. | The 479,844-byte GPX 1.1 validates as XML and contains 3,601 trackpoints with no waypoint-only substitution. | PASS | [assets/TRD_09-gpx-download.txt](../assets/TRD_09-gpx-download.txt); [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_09-gpx-download.txt](../assets/TRD_09-gpx-download.txt) | TRD acceptance mapping and GPX summary. |
| [assets/FIT_05-gpx-download.txt](../assets/FIT_05-gpx-download.txt) | Direct visible action and structural payload validation. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; the downloaded GPX was validated directly.

## Timings

| Step | Timing |
|---|---:|
| Evidence mapping from completed same-run action | <1 min |

## Handoff Notes

- Completed: FIT-backed GPX download is valid and contains real trackpoints.
- Remaining unfinished coverage: None for TRD_09.
- Blocked or not applicable: None.
- State left for the next packet: Track 100005 remains available for reversible metadata checks.
