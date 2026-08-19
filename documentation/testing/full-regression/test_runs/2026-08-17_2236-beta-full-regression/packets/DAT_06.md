# Packet: DAT_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_06
- In scope: Exclude non-GPS FIT and waypoint-only GPX from positive evidence.
- Out of scope: Optional negative import probes performed later through Admin.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01 and DAT_05.
- Required app/data state: Positive fixture manifest available.
- Required browser context: None.

## Allowed Mutations

- Allowed: Audit positive fixture eligibility.
- Not allowed: Count waypoint-only or non-GPS files as positive coverage.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_06 | Audited every positive GPX/FIT fixture against trackpoint/GPS criteria. | No waypoint-only GPX or non-GPS FIT is counted as positive evidence. | All five GPX positives contain real `trkpt` sequences, and the FIT positive converts to 3,601 GPS trackpoints. No waypoint-only or non-GPS file is counted. | PASS | [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt); [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_01-public-source-preflight.txt](../assets/DAT_01-public-source-preflight.txt) | GPX real-track eligibility. |
| [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt) | FIT GPS eligibility. |

## Screenshot Evidence

Not applicable; this is fixture eligibility accounting.

## Timings

| Step | Timing |
|---|---:|
| Eligibility audit | <1 s |

## Handoff Notes

- Completed: Only GPS-bearing track data is counted as positive evidence.
- Remaining unfinished coverage: None for DAT_06.
- Blocked or not applicable: None.
- State left for the next packet: Positive data set remains valid and staged.
