# Packet: MOB_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MOB_05
- In scope: Touch map gestures after tool usage.
- Out of scope: Basic map zoom control usability, covered by MOB_03.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_04
- Required app/data state: Main map available.
- Required browser context: Touch-capable mobile context.

## Allowed Mutations

- Allowed: Open tools and perform gestures.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MOB_05 | Checked available gesture support after mobile viewport testing. | Map gestures (pinch, double-tap, drag) work after using each tool. | Pointer map controls worked at mobile width, but pinch and touch double-tap require a touch/gesture-capable context that this browser runtime does not expose. | BLOCKED | `assets/MOB_05-touch-gestures-blocked.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MOB_05-touch-gestures-blocked.txt | Touch gesture tooling constraint. |

## Timings

| Step | Timing |
|---|---:|
| Capability check and mobile map-control evidence review | <1 min |

## Handoff Notes

- Completed: MOB_05 terminal `BLOCKED`.
- Remaining unfinished coverage: NET_01 through RUN_CLEANUP.
- Blocked or not applicable: Touch map gestures require tooling not available in this run.
- State left for the next packet: Desktop viewport restored to 1280 x 720; signed in on Stats Tracks view with 14 tracks.
