# Packet: APP_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_06
- In scope: Base map style selection while UI theme is dark.
- Out of scope: Theme readability already covered by APP_01 through APP_05.

## Prerequisites

- Required previous coverage IDs or run packets: APP_05
- Required app/data state: Signed in with dark UI theme selected.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open Map panel and change base map style.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_06 | In dark UI theme, opened Map panel and selected each exposed base style: OSM Topo, Swiss Color, Swiss Light, OSM Light, and OSM Dark. | Map theme is independent; available light, dark, grayscale, light-topo, swisstopo, and swisstopo-color styles can be selected with either UI theme. | The five exposed styles could be selected independently in dark UI, but no grayscale style was exposed despite the coverage scope listing it. | FAIL | `assets/APP_06-map-styles.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| APP-01 | P3 | Grayscale base-map style is missing from Map panel despite regression scope listing it. | Open Settings, set dark theme, open Map panel, and inspect/select base-map styles. | Light, dark, grayscale, light-topo, swisstopo, and swisstopo-color styles should be selectable with either UI theme. | Map panel exposes OSM Topo, Swiss Color, Swiss Light, OSM Light, and OSM Dark; grayscale is absent. | `assets/APP_06-map-styles.txt` | Users cannot select the documented/tested grayscale base-map style. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/APP_06-map-styles.txt | Exposed map styles and active-selection observations. |

## Timings

| Step | Timing |
|---|---:|
| Inspect and select base styles | <2 min |

## Handoff Notes

- Completed: APP_06 terminal `FAIL`.
- Remaining unfinished coverage: APP_07 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Signed in, dark UI theme, last selected map style OSM Dark.
