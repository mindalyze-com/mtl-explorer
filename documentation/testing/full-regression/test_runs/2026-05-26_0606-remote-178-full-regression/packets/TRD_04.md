# Packet: TRD_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_04
- In scope: detail charts for elevation, speed, distance, and gain.
- Out of scope: graph controls.

## Prerequisites

- Required previous coverage IDs or run packets: FIT_03.
- Required app/data state: FIT-backed track detail Graphs tab open.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open Graphs tab.
- Not allowed: data changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_04 | Opened the FIT-backed Graphs tab. | Elevation, speed, distance, and gain charts render with readable values. | Graphs tab rendered multiple chart panels with track metric axes/values instead of blank space. | PASS | `assets/FIT_03-fit-graphs.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_03-fit-graphs.webp | Graphs tab charts. |

## Timings

| Step | Timing |
|---|---:|
| Graph render check | Reused from FIT_03 |

## Handoff Notes

- Completed: TRD_04 terminal PASS.
- Remaining unfinished coverage: continue with TRD_05.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
