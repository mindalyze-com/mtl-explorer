# Packet: LOC_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_03
- In scope: Selected locale persists across reload.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_02.
- Required app/data state: de-DE selected; Metric units.
- Required browser context: Statistics and Admin Preferences.

## Allowed Mutations

- Allowed: Reload Statistics and navigate to Preferences.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_03 | Reloaded Statistics and rechecked live formatting plus Preferences selection. | Locale persists across reload. | German separators/dates remained live and the selector/preview still reported de-DE; Metric remained independent. | PASS | [assets/LOC_03-locale-persistence.txt](../assets/LOC_03-locale-persistence.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC_03-locale-persistence.txt](../assets/LOC_03-locale-persistence.txt) | Reloaded Stats and Preferences state. |

## Screenshot Evidence

Direct formatted-text/control evidence is durable; ACC_04 prevents screenshots.

## Timings

| Step | Timing |
|---|---:|
| Reload and settlement | About 0.85 s |

## Handoff Notes

- Completed: de-DE persistence across reload.
- Remaining unfinished coverage: None for LOC_03.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Admin Preferences open; de-DE/Metric retained.
