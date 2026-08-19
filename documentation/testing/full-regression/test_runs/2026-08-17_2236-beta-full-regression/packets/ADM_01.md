# Packet: ADM_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_01
- In scope: Admin overview and grouped section navigation on desktop and mobile.
- Out of scope: Detailed behavior inside each Admin tool.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_04.
- Required app/data state: Healthy signed-in quick install.
- Required browser context: Desktop 1280 x 720 and fresh mobile 390 x 760.

## Allowed Mutations

- Allowed: Section navigation and reversible viewport change.
- Not allowed: Trigger operational mutations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_01 | Opened Admin desktop, enumerated grouped sections/status cards, then used a fresh 390 x 760 tab to navigate Overview→Processing→Overview. | Overview and grouped navigation are reachable/usable on desktop and mobile. | Desktop exposed ten grouped sections and healthy cards. Mobile bottom sheet fit safely, card navigation changed route, and Back to overview restored it. | PASS | [assets/ADM_01-responsive.txt](../assets/ADM_01-responsive.txt); [assets/ADM_01-desktop.jpg](../assets/ADM_01-desktop.jpg); [assets/ADM_01-mobile.jpg](../assets/ADM_01-mobile.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_01-responsive.txt](../assets/ADM_01-responsive.txt) | Desktop/mobile section inventory, route transitions, and viewport cleanup. |
| [assets/ADM_01-desktop.jpg](../assets/ADM_01-desktop.jpg) | Desktop grouped Admin overview. |
| [assets/ADM_01-mobile.jpg](../assets/ADM_01-mobile.jpg) | 390 x 760 mobile Admin overview sheet. |

## Screenshot Evidence

- Paired images show the grouped desktop workspace and safe-width mobile bottom sheet.

## Timings

| Step | Timing |
|---|---:|
| Desktop open | Under 1 s |
| Mobile direct route | About 2 s |
| Mobile section/back transitions | Under 600 ms each |

## Handoff Notes

- Completed: Responsive Admin overview and navigation.
- Remaining unfinished coverage: None for ADM_01.
- Blocked or not applicable: None.
- State left for the next packet: Desktop Admin Overview remains open; mobile viewport reset and tab closed.
