# Packet: FLT_19

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: FLT_19
- In scope: Apply filter pause/resume synchronization, persistence, switch placement, and desktop/mobile parity.
- Out of scope: Shared track-browser functionality, covered by FLT_20.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_18.
- Required app/data state: Eight-track applied year filter and nine-track baseline.
- Required browser context: Authenticated desktop browser; narrow emulation unavailable.

## Allowed Mutations

- Allowed: Toggle Apply filter, navigate to Statistics, and reload.
- Not allowed: Change the saved category selection.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FLT_19 | Pause, verify map/Stats/persistence, resume, and repeat position checks at both widths. | Result, views, persistence, and one switch remain synchronized. | Desktop passed: 8->9 on pause, persisted, then 9->8 on resume with one switch. Mobile viewport is unavailable. | BLOCKED | [assets/FLT_19-apply-switch.txt](../assets/FLT_19-apply-switch.txt); [packets/ACC_04.md](ACC_04.md) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_19-apply-switch.txt](../assets/FLT_19-apply-switch.txt) | Desktop synchronization, reload persistence, switch count, and mobile blocker. |

## Screenshot Evidence

Screenshot capture and narrow viewport emulation are BLOCKED in ACC_04.

## Timings

| Step | Timing |
|---|---:|
| Desktop pause, reload, and resume | 5 min |
| Mobile capability audit | 1 min |

## Handoff Notes

- Completed: Full desktop pause/resume flow and single-switch placement.
- Remaining unfinished coverage: None; mobile parity is terminal BLOCKED.
- Blocked or not applicable: Narrow mobile validation (ACC_04).
- State left for the next packet: Statistics with the eight-track applied year filter resumed.
