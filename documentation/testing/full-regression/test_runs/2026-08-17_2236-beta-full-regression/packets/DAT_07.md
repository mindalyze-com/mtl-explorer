# Packet: DAT_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: DAT_07
- In scope: A repeatable two-point segment crossed by at least two tracks.
- Out of scope: Import and tool behavior, tested later.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_06.
- Required app/data state: Staging area outside `data/gpx/`.
- Required browser context: None.

## Allowed Mutations

- Allowed: Create fully synthetic anonymized GPX fixtures.
- Not allowed: Use, copy, or derive from private local GPX tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_07 | Created two independent timestamped four-point synthetic GPX tracks with common start/end zones and staged them outside the watched folder. | Measuring, comparison, and virtual-race checks have at least two tracks crossing the same two repeatable zones. | `MTL Synthetic Segment A` and `B` cross the same Bern-area start/end zones with slightly different intermediate geometry; both are fully synthetic and checksum-recorded. | PASS | [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_07-segment-fixtures.txt](../assets/DAT_07-segment-fixtures.txt) | Synthetic fixture names, hashes, point counts, staging path, and shared-zone design. |

## Screenshot Evidence

Not applicable; geometry fixtures are staged for later UI checks.

## Timings

| Step | Timing |
|---|---:|
| Create, validate, and stage fixtures | 1.9 s |

## Handoff Notes

- Completed: Two-track crossing fixture is ready.
- Remaining unfinished coverage: None for DAT_07.
- Blocked or not applicable: None.
- State left for the next packet: Synthetic segment files remain outside the watched import folder.
