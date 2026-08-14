# Packet: TRD_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_01.
- In scope: open and identify one GPX-backed and one FIT-backed track through user-facing navigation.
- Out of scope: tab completeness, covered by later Track Details packets.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_15.
- Required app/data state: public GPX and Garmin FIT imports present.
- Required browser context: signed in with Statistics available.

## Allowed Mutations

- Allowed: search, open, and close Track Details.
- Not allowed: alter track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_01 | Opened Statistics > Tracks, searched the exact GPX and FIT filenames, and opened the returned rows. | At least one GPX-backed and one FIT-backed track open; IDs and source filenames are recorded. | `JuraRoute72011.gpx` opened #100000 Jura Route 7; `Activity.fit` opened #100005 Activity.fit. Both exact searches returned one row and opened normal Track Details. | PASS | [identities](../assets/TRD_01-track-identities.txt), [source mapping](../assets/DAT_03-source-mapping.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_01-track-identities.txt](../assets/TRD_01-track-identities.txt) | Exact user-facing origin, filename searches, IDs, and detail names. |
| [assets/DAT_03-source-mapping.txt](../assets/DAT_03-source-mapping.txt) | Frozen source-to-track mapping. |

## Screenshot Evidence

No new screenshot was needed; FIT and GPX detail screenshots already exist in the run assets, and the durable text records the exact fresh navigation.

## Timings

| Step | Timing |
|---|---:|
| FIT search and open | < 2 s |
| GPX search and open | < 2 s |

## Handoff Notes

- Completed: TRD_01.
- Remaining unfinished coverage: TRD_02 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: #100000 GPX Track Details open on Overview from Statistics > Tracks.

