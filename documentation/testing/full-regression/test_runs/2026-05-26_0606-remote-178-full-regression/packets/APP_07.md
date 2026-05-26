# Packet: APP_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_07
- In scope: Selected map style persistence across reload.
- Out of scope: Availability of every style, covered by APP_06.

## Prerequisites

- Required previous coverage IDs or run packets: APP_06
- Required app/data state: Signed in with a non-default map style selected.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Select map style and hard reload.
- Not allowed: Change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_07 | Selected OSM Dark, hard reloaded, reopened Map panel, and inspected active style. | Selected map style persists across reload. | After reload, the dark UI remained active and OSM Dark still had the active check. | PASS | `assets/APP_07-map-style-persistence.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/APP_07-map-style-persistence.txt | Map style persistence observations before and after reload. |

## Timings

| Step | Timing |
|---|---:|
| Select style and reload | <1 min |

## Handoff Notes

- Completed: APP_07 passed.
- Remaining unfinished coverage: APP_08 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Signed in, dark UI theme, OSM Dark selected.
