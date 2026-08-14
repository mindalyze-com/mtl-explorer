# Packet: TRD_10

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_10.
- In scope: persisted activity-type edit and automatic energy recalculation.
- Out of scope: transient custom energy what-if, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_09.
- Required app/data state: FIT #100005 activity Walking with 346.7 Wh net energy.
- Required browser context: Track Details Quality and Overview tabs.

## Allowed Mutations

- Allowed: save Bicycle temporarily and restore Walking.
- Not allowed: leave the record with a changed activity.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_10 | Changed #100005 from Walking to Bicycle in Quality, checked Overview energy, then restored Walking and checked again. | Activity save succeeds and energy updates automatically. | Both PATCH requests returned 200. Net energy changed 346.7→395.1 Wh with Bicycle and returned to 346.7 Wh after restoring Walking. | PASS | [activity and energy](../assets/TRD_10-activity-energy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_10-activity-energy.txt](../assets/TRD_10-activity-energy.txt) | UI values, option list, HTTP results, energy delta, and restored state. |

## Screenshot Evidence

Exact values and HTTP results are recorded in text; no screenshot is needed for the reversible save sequence.

## Timings

| Step | Timing |
|---|---:|
| Save Bicycle | 1.365 s server time |
| Restore Walking | 0.738 s server time |

## Handoff Notes

- Completed: TRD_10.
- Remaining unfinished coverage: TRD_11 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: #100005 restored to Walking with 346.7 Wh; Overview open.

