# Packet: DAT_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: DAT_05.
- In scope: obtain and directly validate at least one public FIT activity file with GPS positions.
- Out of scope: product FIT conversion/import behavior.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_01-DAT_04.
- Required app/data state: public staging folder outside the watched import path.
- Required browser context: none.

## Allowed Mutations

- Allowed: download Garmin's public `Activity.fit` into staging and decode it with Garmin's official SDK.
- Not allowed: copy it to the watched import folder yet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DAT_05 | Downloaded Garmin's official FIT SDK `Activity.fit` example; recorded source/license/checksum/size/header; decoded it with the official JavaScript SDK and counted GPS-bearing records. | At least one public FIT activity file contains GPS positions. | `Activity.fit` is a valid 94,096-byte FIT file with matching `.FIT` header, true integrity, no decode errors, and 3,601 timestamped GPS-bearing record messages. | PASS | [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DAT_05-public-fit.txt](../assets/DAT_05-public-fit.txt) | Public source/license metadata, checksum/size, FIT integrity, and GPS/timestamp record counts. |

## Screenshot Evidence

Not applicable; this packet validates staged binary activity content.

## Timings

| Step | Timing |
|---|---:|
| Download and official SDK validation | 3 s |

## Handoff Notes

- Completed: one public, valid, fully timestamped GPS-bearing FIT activity is staged.
- Remaining unfinished coverage: DAT_06 onward and DAT_03 imported mapping.
- Blocked or not applicable: none.
- State left for the next packet: `Activity.fit` is in staging, not the watched folder.
