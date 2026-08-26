# Packet: LOC_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_02
- In scope: Changing locale updates formatting across the app without reload artifacts.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_01.
- Required app/data state: en-GB/Metric at start; populated Statistics.
- Required browser context: Admin Preferences and Statistics in one session.

## Allowed Mutations

- Allowed: Change locale from en-GB to de-DE; do not reload.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_02 | Selected de-DE and immediately checked Preferences and Statistics without reload. | Formatting updates across the app without artifacts. | Preview, decimal/group separators, and all sampled dates updated immediately; no prior en-GB date remained. | PASS | [assets/LOC_02-locale-switch.txt](../assets/LOC_02-locale-switch.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC_02-locale-switch.txt](../assets/LOC_02-locale-switch.txt) | Locale choices and exact before/after formats. |

## Screenshot Evidence

Direct formatted-text evidence is durable; ACC_04 prevents saved screenshots.

## Timings

| Step | Timing |
|---|---:|
| Locale switch | Under 0.2 s |
| Statistics reopen | About 0.3 s |

## Handoff Notes

- Completed: No-reload en-GB→de-DE formatting update.
- Remaining unfinished coverage: None for LOC_02.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Statistics Overview open; de-DE/Metric selected.
