# Packet: TRD_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TRD_11.
- In scope: transient rider-weight energy recalculation and non-persistence.
- Out of scope: saving a new rider weight.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_10.
- Required app/data state: #100005 restored to Walking, saved rider mass 75 kg.
- Required browser context: Track Details Overview.

## Allowed Mutations

- Allowed: change the transient what-if slider and discard it.
- Not allowed: select Save or leave a persistent rider-weight change.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_11 | Opened Adjust rider weight, changed the transient range from 75 to 100 kg, inspected recalculation, closed without Save, reloaded, and reopened it. | Custom weight updates displayed energy without permanent save. | Scenario values changed 346.7→462.2 Wh and 702→936 W with +115.6 Wh delta. After discard/reload, 75 kg and original energy/power returned. | PASS | [energy what-if](../assets/TRD_11-energy-what-if.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_11-energy-what-if.txt](../assets/TRD_11-energy-what-if.txt) | Baseline, scenario, and post-reload non-persistent values. |

## Screenshot Evidence

Exact before/after numeric values provide direct evidence; no screenshot is needed.

## Timings

| Step | Timing |
|---|---:|
| Scenario recalculation | < 1 s |
| Reload persistence check | < 2 s |

## Handoff Notes

- Completed: TRD_11.
- Remaining unfinished coverage: TRD_12 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: #100005 Overview open; saved rider mass remains 75 kg; no what-if panel open.

