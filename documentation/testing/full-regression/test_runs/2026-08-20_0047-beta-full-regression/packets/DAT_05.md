# Packet: DAT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_05
- In scope: At least one public GPS-bearing FIT activity file.
- Out of scope: Import and download UI checks.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_04.
- Required app/data state: Required app image running; unwatched logs staging available.
- Required browser context: None.

## Allowed Mutations

- Allowed: Download the official public FIT example into disposable unwatched staging and run read-only conversion validation.
- Not allowed: Import before FIT_01 or retain the fixture in repository artifacts.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_05 | Download Garmin's official `Activity.fit`; checksum it; use the image's GPSBabel to convert an unwatched copy and inspect trackpoints/positions. | At least one public FIT activity contains GPS positions. | The 94,096-byte FIT converted to 3,601 timestamped trackpoints with 3,601 distinct positions. | PASS | [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) | Source/license, checksum, size, and GPS-bearing validation. |

## Screenshot Evidence

Not useful for FIT binary validation.

## Timings

| Step | Timing |
|---|---:|
| Download, conversion, and validation | <10 s |

## Handoff Notes

- Completed: Official public GPS-bearing FIT fixture staged and validated.
- Remaining unfinished coverage: None for DAT_05.
- Blocked or not applicable: None.
- State left for the next packet: FIT remains outside `data/gpx/` until FIT_01.
