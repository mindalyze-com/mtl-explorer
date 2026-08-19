# Packet: SGN_09

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SGN_09
- In scope: Browser Back and Forward between application views.
- Out of scope: Browser history outside the application.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_08.
- Required app/data state: Healthy signed-in application.
- Required browser context: Main map with populated data.

## Allowed Mutations

- Allowed: Navigate between read-only Admin views and use browser history.
- Not allowed: Run Admin actions or change stored data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_09 | Navigated from Admin Overview to Processing, used browser Back, then Forward, and checked each restored route and heading for visible errors. | Back and Forward restore the expected views without errors. | Back restored `/mtl/admin` with Overview; Forward restored `/mtl/admin/processing` with Processing. No visible error text appeared. | PASS | [assets/SGN_09-history.txt](../assets/SGN_09-history.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_09-history.txt](../assets/SGN_09-history.txt) | Routes, headings, and error checks across Back and Forward. |

## Screenshot Evidence

Not available because ACC_04 blocks screenshots; routed URL and semantic heading evidence was sufficient.

## Timings

| Step | Timing |
|---|---:|
| Each routed Admin navigation | Under 4 s |
| Each browser history operation | About 10 s |

## Handoff Notes

- Completed: Back and Forward navigation across two routed Admin views.
- Remaining unfinished coverage: None for SGN_09.
- Blocked or not applicable: None.
- State left for the next packet: Signed in on Admin Processing; map data remains unchanged.
