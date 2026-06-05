# Packet: MED_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MED_01
- In scope: Determine whether media-layer photo pins apply to this run.
- Out of scope: Media preview/navigation, covered by later MED IDs.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_03.
- Required app/data state: Current quick-install dataset.
- Required browser context: Authenticated browser/API context.

## Allowed Mutations

- Allowed: Read-only media API checks.
- Not allowed: Adding synthetic/private media files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_01 | Queried media availability with world bounds and with-location media APIs. | If indexed media exists, toggling the media layer should show photo pins. | No indexed media exists in this configured run: both media APIs returned HTTP 200 with `[]`, and the target data folder contains GPX/routing/database files but no media/photo inputs. | NOT APPLICABLE | [assets/MED_media-availability.txt](../assets/MED_media-availability.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_media-availability.txt](../assets/MED_media-availability.txt) | Media API availability check showing zero indexed media. |

## Timings

| Step | Timing |
|---|---:|
| Media API availability check | ~2s |

## Handoff Notes

- Completed: MED_01 NOT APPLICABLE.
- Remaining unfinished coverage: MED_02 onward.
- Blocked or not applicable: Media photo coverage is not applicable without indexed media assets.
- State left for the next packet: No server data changed.
