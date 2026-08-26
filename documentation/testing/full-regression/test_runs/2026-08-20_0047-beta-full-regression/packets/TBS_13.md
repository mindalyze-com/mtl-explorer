# Packet: TBS_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_13
- In scope: Pointer/keyboard activation of filtered Statistics summary on desktop and mobile.
- Out of scope: Trends media chart, covered by TBS_14.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_12.
- Required app/data state: Statistics Overview showing 1 of 7 tracks.
- Required browser context: Desktop browser; mobile emulation unavailable.

## Allowed Mutations

- Allowed: Activate summary and navigate between Stats and Filter.
- Not allowed: Change active geo filter.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_13 | Activate filtered summary with pointer and keyboard on desktop/mobile. | Every activation opens Filter directly. | Desktop pointer and Enter both pass; mobile viewport cannot be established. | BLOCKED | [assets/TBS_13-stats-summary-navigation.txt](../assets/TBS_13-stats-summary-navigation.txt); [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_13-stats-summary-navigation.txt](../assets/TBS_13-stats-summary-navigation.txt) | Desktop activations and exact mobile blocker. |

## Screenshot Evidence

Screenshot capture and mobile viewport emulation are BLOCKED in ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Desktop pointer and keyboard activation | 3 min |
| Mobile capability audit | 1 min |

## Handoff Notes

- Completed: Desktop pointer and keyboard summary navigation.
- Remaining unfinished coverage: None; mobile activation is terminal BLOCKED.
- Blocked or not applicable: Mobile viewport validation (ACC_04).
- State left for the next packet: Filter open with one-track Lannion circle result.
