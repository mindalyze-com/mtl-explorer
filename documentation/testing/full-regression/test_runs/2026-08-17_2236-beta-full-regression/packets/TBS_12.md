# Packet: TBS_12

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_12
- In scope: Shared resolved track set for an active geo-drawn filter in the map and all Statistics tabs, before and after reload fallback ID resolution.
- Out of scope: Other filter criteria and unrelated Statistics functions.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_11.
- Required app/data state: Stable 13-track set; prior temporary highlight exclusion restored to Included.
- Required browser context: Map, Filter, and Statistics Overview, Trends, and Tracks.

## Allowed Mutations

- Allowed: Draw and retain one temporary rectangular geo filter for TBS_13.
- Not allowed: Change the controlled track set or track curation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_12 | Curated one track, narrowed the geo filter to a different track, and compared map and Statistics on the fixed build at desktop and mobile sizes. | Statistics only merges curation overrides for tracks inside the current resolved set. | Tracks showed only the in-scope Lannion row before and after reload; the stale Mosel override was not resurrected. | FIXED | [details](../assets/TBS_12-remediation.txt); [desktop](../assets/TBS_12-fixed-desktop.webp); [mobile](../assets/TBS_12-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-006 | P1 | Statistics Tracks can include a stale curated track outside an active geo filter. | Temporarily exclude Mosel from highlights, restore it to Included, draw a rectangle selecting only Lannion, then open Statistics > Tracks > All without reloading. | Tracks contains the same one matching track as map, Filter, Overview, and Trends. | Tracks contains both Lannion and the stale Mosel row until a page reload; the other surfaces contain only Lannion. | [assets/TBS_12-geo-resolved-set.txt](../assets/TBS_12-geo-resolved-set.txt) | Filtered statistics are internally inconsistent and can overstate totals until reload. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_12-geo-resolved-set.txt](../assets/TBS_12-geo-resolved-set.txt) | Active-filter counts and rows before and after reload. |

## Screenshot Evidence

Unavailable under ACC_04. Exact map, filter, summary, row, and post-reload DOM text provides direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Draw and compare map/Statistics | About 10 s |
| Reload and fallback-ID comparison | About 5 s |

## Handoff Notes

- Completed: Geo-filtered shared-set comparison before and after reload.
- Remaining unfinished coverage: None for TBS_12.
- Blocked or not applicable: Screenshot capture remains blocked under ACC_04.
- State left for the next packet: The one-track rectangle remains active, Statistics Tracks is open, and the post-reload set is correct.

## Remediation Verification

- Finding FR-006 is `FIXED`: out-of-scope track overrides are pruned from Statistics.
- Automated coverage preserves in-scope overrides while rejecting stale rows after scope changes.
