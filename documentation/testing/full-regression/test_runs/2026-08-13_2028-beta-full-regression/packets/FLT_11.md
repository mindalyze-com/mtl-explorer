# Packet: FLT_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_11.
- In scope: parameter change while an exact category selection is active, including newly discovered category behavior.
- Out of scope: unavailable selected categories, covered by FLT_13.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_10.
- Required app/data state: year categories 2010 and 2013 available in the dataset.
- Required browser context: Tracks by year criteria and Included categories.

## Allowed Mutations

- Allowed: restrict/expand year parameters and select 2010 exactly.
- Not allowed: switch filter view during the check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_11 | Limited years to 2010, selected 2010 as an exact category set, then expanded TO YEAR to 2013 and inspected selection. | Existing exact selection remains; newly discovered categories stay unchecked. | The map remained at two 2010 tracks. Category state changed 1/1→1/2, with 2010 checked, new 2013 unchecked, and All categories false. | PASS | [selection retention](../assets/FLT_11-category-selection-retention.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_11-category-selection-retention.txt](../assets/FLT_11-category-selection-retention.txt) | Parameter, exact selection, discovery, and checkbox states. |

## Screenshot Evidence

Exact checkbox properties and category/result counts provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Exact selection apply | < 1 s |
| Expanded year resolution | < 1 s |

## Handoff Notes

- Completed: FLT_11.
- Remaining unfinished coverage: FLT_12 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Included categories open; 2010 checked, 2013 unchecked; year range 2010-2013; map result 2/12.

