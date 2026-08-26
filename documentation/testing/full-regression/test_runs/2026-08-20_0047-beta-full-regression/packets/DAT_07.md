# Packet: DAT_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_07
- In scope: A repeatable two-point segment crossed by at least two tracks.
- Out of scope: Importing and exercising segment UI before its queue position.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_06.
- Required app/data state: Disposable unwatched fixture staging.
- Required browser context: None.

## Allowed Mutations

- Allowed: Generate fully synthetic anonymized GPX fixtures outside watched folders.
- Not allowed: Use or derive from private local tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_07 | Generate two timestamped six-point tracks with nearly identical start/end zones and distinct intermediate geometry. | Two or more safe tracks cross a repeatable two-point segment for measure/comparison/race checks. | Alpha and Bravo share start/end zones near Bern, each has six real trackpoints, and all content is fully synthetic. | PASS | [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) | Fixture checksums, point counts, endpoints, and privacy statement. |

## Screenshot Evidence

Not useful until the fixtures are exercised in map tools.

## Timings

| Step | Timing |
|---|---:|
| Fixture generation and validation | <1 s |

## Handoff Notes

- Completed: Safe repeated-segment data is ready.
- Remaining unfinished coverage: None for DAT_07.
- Blocked or not applicable: None.
- State left for the next packet: Segment fixtures remain unwatched and will be imported before MCT/AVR.
