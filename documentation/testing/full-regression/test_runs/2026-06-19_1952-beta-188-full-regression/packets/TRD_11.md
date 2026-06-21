# Packet: TRD_11

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_11
- In scope: Verify Energy what-if recalculation updates displayed values without permanently saving.
- Out of scope: Permanent activity-type save, covered by TRD_10.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_10.
- Required app/data state: Track #100005 Overview available with energy section.
- Required browser context: authenticated desktop detail page.

## Allowed Mutations

- Allowed: Change transient rider-weight what-if value and close without Save.
- Not allowed: Save a permanent rider-weight change.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_11 | Opened Adjust rider weight, changed 75 kg to 85 kg without saving, observed recalculated dialog values, closed without Save, then read API final state and what-if API. | What-if values update in the UI without permanently saving. | Dialog changed from 346.7 Wh to 392.9 Wh / +46.2 Wh at 85 kg. After closing without Save, API still reported 75.0 kg and 346.67 Wh; what-if API returned the same adjusted 392.89 Wh without persistence. | PASS | [assets/TRD_11-energy-what-if.txt](../assets/TRD_11-energy-what-if.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_11-energy-what-if.txt](../assets/TRD_11-energy-what-if.txt) | Dialog before/after, no-save close, and API final/what-if values. |

## Screenshot Evidence

No screenshot asset was captured for this packet; direct DOM/API evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Energy what-if check | ~3 min |

## Handoff Notes

- Completed: TRD_11.
- Remaining unfinished coverage: TRD_12 onward.
- Blocked or not applicable: none.
- State left for the next packet: Track #100005 permanent energy state remains 75 kg / 346.67 Wh.
