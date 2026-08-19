# Packet: DAT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_05
- In scope: At least one public FIT activity with GPS positions.
- Out of scope: Product import and UI behavior (FIT_01-FIT_06).

## Prerequisites

- Required previous coverage IDs or run packets: DAT_04.
- Required app/data state: Public FIT fixture staged outside watched folder.
- Required browser context: None.

## Allowed Mutations

- Allowed: Convert a staged copy outside the watcher for structural preflight.
- Not allowed: Count a non-GPS FIT file as positive evidence.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_05 | Downloaded Garmin's public `Activity.fit` and converted it outside the watched folder with the app image's GPSBabel 1.10.0. | The FIT fixture is a GPS-bearing activity suitable for positive import coverage. | Conversion produced a 595,045-byte GPX containing 3,601 real trackpoints with timestamps, proving the FIT fixture has GPS positions. | PASS | [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_05-fit-preflight.txt](../assets/DAT_05-fit-preflight.txt) | FIT source, checksum, GPSBabel version, and converted trackpoint proof. |

## Screenshot Evidence

Not applicable; this is binary fixture preflight evidence.

## Timings

| Step | Timing |
|---|---:|
| Container GPSBabel preflight | 1.5 s |

## Handoff Notes

- Completed: Public FIT fixture proven GPS-bearing.
- Remaining unfinished coverage: None for DAT_05.
- Blocked or not applicable: None.
- State left for the next packet: Original FIT and preflight GPX remain outside watched import folder.
