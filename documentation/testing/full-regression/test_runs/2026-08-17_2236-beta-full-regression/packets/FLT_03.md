# Packet: FLT_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_03
- In scope: Pick a view, parameter appearance, immediate auto-apply, clearing, and synchronization across chip/count/map/legend/statistics.
- Out of scope: Persistence across reload covered by FLT_04.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_02 and FLT_01.
- Required app/data state: Fifteen tracks including two named `MTL Synthetic`.
- Required browser context: Filter criteria, map, and Statistics Overview.

## Allowed Mutations

- Allowed: Switch to Activities by keyword, set/clear Keyword, and navigate read-only views.
- Not allowed: Leave a restrictive keyword active after this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_03 | Selected Activities by keyword, entered a keyword, and inspected Filter/map identity and counts on the fixed build at desktop and mobile sizes. | Parameter applies immediately and remains identified with the active view. | `Activities by keyword · Synthetic` appeared in the map and Filter Current result chip while counts stayed synchronized. | FIXED | [details](../assets/FLT_03-remediation.txt); [desktop](../assets/FLT_03-fixed-desktop.webp); [mobile](../assets/FLT_03-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-005 | P2 | Active filter/parameter is not shown as a chip. | Select Activities by keyword and enter `Synthetic`. | An active chip reflects the keyword while all filtered views update. | All filtered views update, but no active filter or keyword chip is rendered. | [assets/FLT_03-auto-apply.txt](../assets/FLT_03-auto-apply.txt) | Users lack the required explicit chip representation of active criteria. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_03-auto-apply.txt](../assets/FLT_03-auto-apply.txt) | Parameter, filtered/reset counts, legend, Statistics values, and chip assertion. |

## Screenshot Evidence

Unavailable under ACC_04. Exact rendered control values, counts, totals, and legend labels provide direct DOM evidence.

## Timings

| Step | Timing |
|---|---:|
| Select view and enter keyword | About 3 s |
| Cross-view synchronization check | About 5 s |
| Clear and reset check | About 5 s |

## Handoff Notes

- Completed: View selection, parameter appearance, auto-apply, clear, and cross-view synchronization.
- Remaining unfinished coverage: None for FLT_03; missing chip is terminal and tracked under FR-005.
- Blocked or not applicable: None.
- State left for the next packet: Statistics Overview open; Activities by keyword active with empty Keyword and all fifteen tracks.

## Remediation Verification

- Finding FR-005 is `FIXED`: the active string criterion is included with the saved-view identity.
- Clearing the criterion still restores the unfiltered result.
