# Packet: APP_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: APP_01
- In scope: Immediate whole-UI light/dark theme switching.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_07.
- Required app/data state: Signed-in synchronized application.
- Required browser context: Admin Preferences.

## Allowed Mutations

- Allowed: Toggle Light/Dark and leave Dark selected for following packets.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_01 | Switched light→dark→light→dark while reading root/pressed state and continued using Preferences. | Whole UI re-themes immediately. | Root theme and exclusive pressed state changed immediately on every toggle without reload; panel remained fully usable. | PASS | [assets/APP_01-theme-switch.txt](../assets/APP_01-theme-switch.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/APP_01-theme-switch.txt](../assets/APP_01-theme-switch.txt) | Exact root/pressed transition sequence. |

## Screenshot Evidence

Live desktop inspection confirmed immediate visual changes. Direct root/control state is durable; ACC_04 prevents saved screenshots.

## Timings

| Step | Timing |
|---|---:|
| Each theme switch | Under 0.2 s |

## Handoff Notes

- Completed: Immediate two-way global theme changes.
- Remaining unfinished coverage: None for APP_01.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Dark UI theme selected in Admin Preferences.
