# Packet: DAT_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DAT_07.
- In scope: create a repeatable two-zone segment crossed by at least two safe test tracks.
- Out of scope: import and measuring/comparison/virtual-race execution.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01-DAT_06.
- Required app/data state: separate synthetic staging folder.
- Required browser context: none.

## Allowed Mutations

- Allowed: create fully synthetic anonymized GPX data in packet assets and copy it to remote staging.
- Not allowed: use, copy, vendor, or derive from private local GPX tracks; import the synthetic files yet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_07 | Created two fully synthetic 12-point timestamped tracks with slightly offset geometry that both cross repeatable 100 m start/end zones; copied them to remote staging and verified checksums/endpoints. | At least two tracks cross the same repeatable two-point segment without using private GPX data. | Both synthetic tracks begin within the start zone centered at `46.950200,7.399800` and end within the zone centered at `47.050200,7.499800`; all 24 points are timestamped. | PASS | [assets/DAT_07-synthetic-shared-zone.txt](../assets/DAT_07-synthetic-shared-zone.txt); [assets/DAT_07-synthetic-shared-a.gpx](../assets/DAT_07-synthetic-shared-a.gpx); [assets/DAT_07-synthetic-shared-b.gpx](../assets/DAT_07-synthetic-shared-b.gpx) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_07-synthetic-shared-zone.txt](../assets/DAT_07-synthetic-shared-zone.txt) | Zone coordinates, checksums, point counts, endpoints, and privacy provenance. |
| [assets/DAT_07-synthetic-shared-a.gpx](../assets/DAT_07-synthetic-shared-a.gpx) | Fully synthetic track A. |
| [assets/DAT_07-synthetic-shared-b.gpx](../assets/DAT_07-synthetic-shared-b.gpx) | Fully synthetic track B. |

## Screenshot Evidence

Not applicable; UI segment evidence belongs to the later MCT/AVR packets.

## Timings

| Step | Timing |
|---|---:|
| Generate, copy, and validate | < 1 min |

## Handoff Notes

- Completed: repeatable two-zone/two-track synthetic prerequisite is staged.
- Remaining unfinished coverage: IMP_01 onward and deferred DAT_03 mapping.
- Blocked or not applicable: none.
- State left for the next packet: positive public data and synthetic shared-zone data are staged outside the watched import folder; live dataset remains empty.
