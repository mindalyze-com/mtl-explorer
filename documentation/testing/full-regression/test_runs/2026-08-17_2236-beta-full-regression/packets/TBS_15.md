# Packet: TBS_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_15
- In scope: Grouping/sub-unit changes, shared timelines, zero-value slots, separate undated media, and track-related versus all-indexed filter scope.
- Out of scope: Period mosaic controls and viewer navigation.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_14.
- Required app/data state: Stable 13-track set and the prescribed six generated media files.
- Required browser context: Statistics Trends Charts and Filter date criteria.

## Allowed Mutations

- Allowed: Change trend grouping/sub-unit/mode and temporarily apply/reset a 2013 date filter.
- Not allowed: Add media outside the prescribed fixture set or leave a filter active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_15 | Compared quarter/month timelines, selected month 08, inspected every series/slot, then compared Matched only with Media history under a one-track 2013 filter. | Common timelines retain zero slots; undated media stays separate; track-related follows the filter while all indexed ignores it. | All eight charts shared exact timelines and kept zero photo/video/activity points. Month 08 retained one slot and reported six photos. Matched only reduced media to zero under the 2013 filter; Media history restored the indexed range/populated 2026 column. The prescribed six-file set contains no undated item, so that child check could not run. | BLOCKED | [assets/TBS_15-media-timeline.txt](../assets/TBS_15-media-timeline.txt); [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_15-media-timeline.txt](../assets/TBS_15-media-timeline.txt) | Axes, point counts, zero slots, scope filtering, cleanup, and blocker. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Prescribed six media items, all with capture timestamps. |

## Screenshot Evidence

Unavailable under ACC_04. Rendered SVG axes/point paths, visible help, tooltip values, and manifest timestamps provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Grouping/sub-unit and slot comparison | About 8 s |
| Filter-scope comparison | About 9 s |
| Undated precondition audit and cleanup | About 3 s |

## Handoff Notes

- Completed: Common timeline, zero-slot, sub-unit, and media-scope checks.
- Remaining unfinished coverage: None for TBS_15; the packet is terminal BLOCKED because the required undated fixture does not exist.
- Blocked or not applicable: Undated compact drill-down only; every prescribed media item is dated.
- State left for the next packet: No active filter; 13 tracks; Trends Charts open; YYYY grouping; Media history selected; six dated photos.
