# Packet: MAP_12

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_12
- In scope: Swiss Mobility route popup applicability.
- Out of scope: planner routing, covered by PLN.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_01.
- Required app/data state: quick-install map configuration.
- Required browser context: signed-in desktop/API context.

## Allowed Mutations

- Allowed: inspect map config/API docs.
- Not allowed: enable external overlays manually.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_12 | Probed map config and API docs for Swiss Mobility route support. | Where applicable, Swiss Mobility routes popup shows nearby official routes and closes cleanly. | No Swiss Mobility route layer/API was exposed in this quick-install configuration; the conditional popup check is not applicable. | NOT APPLICABLE | `assets/MAP_12-swiss-config-probe.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MAP_12-swiss-config-probe.txt | Map config/API probe for Swiss Mobility support. |

## Timings

| Step | Timing |
|---|---:|
| Applicability probe | <1m |

## Handoff Notes

- Completed: MAP_12 terminal NOT APPLICABLE.
- Remaining unfinished coverage: continue with TRD_01.
- Blocked or not applicable: Swiss Mobility popup support was not exposed in this run.
- State left for the next packet: no data changed.
