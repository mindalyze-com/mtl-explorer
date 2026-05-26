# Packet: MOB_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MOB_03
- In scope: Tables/lists, charts, and map controls at narrow mobile width.
- Out of scope: Touch gestures, covered by MOB_04 and MOB_05.

## Prerequisites

- Required previous coverage IDs or run packets: MOB_02
- Required app/data state: Signed in; 14 tracks visible.
- Required browser context: 390 x 844 viewport.

## Allowed Mutations

- Allowed: Open Stats tabs and click map zoom.
- Not allowed: Change data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MOB_03 | Opened Stats, switched Trends and Tracks tabs, inspected mobile row content and page width, then clicked map Zoom in. | Tables, charts, and map controls stay usable; no text overflows. | Stats tabs were clickable, Tracks showed mobile list rows and sort controls, document width stayed at 390 px, and Zoom in changed scale from 1000 km to 500 km. | PASS | `assets/MOB_03-mobile-usability.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MOB_03-mobile-usability.txt | Narrow viewport usability and overflow checks. |

## Timings

| Step | Timing |
|---|---:|
| Mobile Stats and map controls | <3 min |

## Handoff Notes

- Completed: MOB_03 passed.
- Remaining unfinished coverage: MOB_04 through RUN_CLEANUP.
- Blocked or not applicable: None for this row.
- State left for the next packet: Desktop viewport restored after mobile checks.
