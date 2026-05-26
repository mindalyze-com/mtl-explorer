# Packet: ADM_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_09
- In scope: Admin Attribution content for map/data/library sources.
- Out of scope: Verifying external attribution links.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_08
- Required app/data state: Admin workspace available.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open Attribution panel.
- Not allowed: Navigate away to external attribution sites.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_09 | Opened Admin -> Attribution and reviewed listed sources. | Shows expected map/data sources. | Attribution listed expected rendering, basemap, trail overlay, chart, location search, conversion, and routing sources including OpenStreetMap, Protomaps, swisstopo, SchweizMobil, GeoNames, GPSBabel, and BRouter. | PASS | `assets/ADM_09-attribution.webp`, `assets/ADM_09-attribution.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_09-attribution.webp | Screenshot of the Attribution panel. |
| assets/ADM_09-attribution.txt | Compact list of attribution entries. |

## Timings

| Step | Timing |
|---|---:|
| Open and inspect Attribution | <1 min |

## Handoff Notes

- Completed: ADM_09 passed.
- Remaining unfinished coverage: ADM_10 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Attribution panel is open; freshness banner remains visible.
