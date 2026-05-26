# Packet: SRC_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SRC_03
- In scope: Clear the selected search marker cleanly.
- Out of scope: No-result query handling.

## Prerequisites

- Required previous coverage IDs or run packets: SRC_02
- Required app/data state: Bern search marker visible on the map.
- Required browser context: Signed-in desktop browser.

## Allowed Mutations

- Allowed: Clear the search marker.
- Not allowed: Change imported data or app configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SRC_03 | Clicked the Bern marker's `Clear search marker` control. | Search marker is removed cleanly. | Marker and clear-button DOM nodes were removed, and the map remained usable with `10 / 10 Tracks`. | PASS | `assets/SRC_03-marker-cleared.webp`, `assets/SRC_03-marker-cleared.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SRC_03-marker-cleared.webp | Screenshot after clearing the marker. |
| assets/SRC_03-marker-cleared.txt | DOM evidence that marker and clear-button nodes were removed. |

## Timings

| Step | Timing |
|---|---:|
| Clear marker and verify DOM | <1 min |

## Handoff Notes

- Completed: SRC_03 passed.
- Remaining unfinished coverage: SRC_04 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Map visible, no location-search marker present.
