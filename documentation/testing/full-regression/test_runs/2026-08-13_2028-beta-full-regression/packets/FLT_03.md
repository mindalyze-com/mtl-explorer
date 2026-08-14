# Packet: FLT_03

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_03.
- In scope: view selection, parameter appearance, automatic application, reset, map/count/legend/stats synchronization.
- Out of scope: reload persistence.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_02.
- Required app/data state: 12-track all-results baseline.
- Required browser context: Filter overview and Statistics.

## Allowed Mutations

- Allowed: select Activities by keyword, set Jura, reset it, and restore all tracks.
- Not allowed: leave a restrictive criterion active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_03 | Selected Activities by keyword, entered Jura, observed before/after Apply, checked map/legend/Stats, then reset and repeated before/after Apply. | Parameters appear and every edit/removal auto-applies immediately across chips/count/map/legend/stats. | Parameters appeared, and all dependent views synchronized correctly after Apply. However, entering or resetting the parameter did nothing to live results until the explicit Apply button was selected. | FAIL | [parameter apply log](../assets/FLT_03-parameter-apply.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-03-P2 | P2 | Filter parameter edits require Apply instead of auto-applying. | Filter > Activities by keyword > Criteria; enter Jura or Reset criteria; wait. | Live result, chip, map, legend, and stats update immediately. | All remain on the old result until Apply is clicked. | [parameter apply log](../assets/FLT_03-parameter-apply.txt) | Users can read stale results while editing and must perform an undocumented extra commit step relative to the tested contract. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_03-parameter-apply.txt](../assets/FLT_03-parameter-apply.txt) | Before/after Apply state across all required surfaces and restored baseline. |

## Screenshot Evidence

Exact live counts and totals before/after Apply prove the temporal defect more directly than a screenshot.

## Timings

| Step | Timing |
|---|---:|
| Wait after keyword edit | 0.7 s |
| Apply to synchronized result | < 1 s |
| Reset and Apply | < 2 s |

## Handoff Notes

- Completed: FLT_03 is terminal `FAIL` with FLT-03-P2.
- Remaining unfinished coverage: FLT_04 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Activities by keyword active with no criteria and all 12 tracks restored.
