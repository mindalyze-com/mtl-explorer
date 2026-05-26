# Packet: HMO_03

## Scope

- Coverage source: documentation/testing/frontend-regression-test-plan.md
- Coverage ID or run packet: HMO_03
- In scope: Verify heatmap updates after changing filters.
- Out of scope: General filter parameter semantics already covered by FLT packets.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_01, HMO_02.
- Required app/data state: Heatmap enabled; filter system available; Lannion track visible in all-track view.
- Required browser context: Desktop browser session authenticated as `mtl`.

## Allowed Mutations

- Allowed: Enable filtering, select `Activities by keyword`, set and clear keyword text.
- Not allowed: Change source track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| HMO_03 | Enabled heatmap at Lannion, selected `Activities by keyword`, entered `Jura`, then cleared the keyword. | Heatmap updates when filters change. | With empty keyword, Lannion showed `10 / 10 Tracks` and the local heatmap. Entering `Jura` reduced the map to `1 / 10 Tracks` and removed the Lannion track/heatmap. Clearing the keyword restored `10 / 10 Tracks` and the Lannion heatmap. | PASS | assets/HMO_03-all-tracks-heatmap.webp; assets/HMO_03-jura-filter-heatmap.webp; assets/HMO_03-restored-heatmap.webp; assets/HMO_03-filter-heatmap.txt |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/HMO_03-all-tracks-heatmap.webp | Lannion all-track heatmap before applying keyword filter. |
| assets/HMO_03-jura-filter-heatmap.webp | `Jura` filter applied; Lannion heatmap gone with 1/10 visible tracks. |
| assets/HMO_03-restored-heatmap.webp | Keyword cleared; Lannion heatmap returned with 10/10 tracks. |
| assets/HMO_03-filter-heatmap.txt | Filter action and observed count/heatmap transitions. |

## Timings

| Step | Timing |
|---|---:|
| Filter heatmap update test | ~9 min |

## Handoff Notes

- Completed: HMO_03.
- Remaining unfinished coverage: GPS_01 onward.
- Blocked or not applicable: None.
- State left for the next packet: Lannion viewport; filtering on with empty keyword; heatmap enabled; Swiss overlays still enabled but visually irrelevant outside Switzerland.
