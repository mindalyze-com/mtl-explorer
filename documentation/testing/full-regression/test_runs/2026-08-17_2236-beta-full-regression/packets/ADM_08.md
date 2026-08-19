# Packet: ADM_08

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_08
- In scope: Server log lines load and refresh.
- Out of scope: Server lifecycle and processing-job behavior.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_07.
- Required app/data state: Normal beta-image installation, authenticated admin.
- Required browser context: Desktop Admin > Server log.

## Allowed Mutations

- Allowed: Refresh the log, change requested line count, and toggle wrapping.
- Not allowed: Modify logging configuration or server files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_08 | Loaded Server log, refreshed it, changed 200 to 50 lines, and toggled wrapping. | Log lines load and refresh. | The timestamped log loaded, Refresh changed the content and returned `just now`, the smaller request returned a bounded result, and wrapping applied immediately without an error. | PASS | [assets/ADM_08-server-log.txt](../assets/ADM_08-server-log.txt); [assets/ADM_08-server-log.jpg](../assets/ADM_08-server-log.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_08-server-log.txt](../assets/ADM_08-server-log.txt) | Route, before/after refresh observations, sample timestamps, line counts, and control state. |
| [assets/ADM_08-server-log.jpg](../assets/ADM_08-server-log.jpg) | Loaded log viewer after the 50-line and Wrap control checks. |

## Screenshot Evidence

- The clipped Admin panel preserves the loaded timestamped lines, requested-line
  selector, recent-update marker, Wrap state, and Refresh action.

## Timings

| Step | Timing |
|---|---:|
| Initial log load | Under 1 s |
| Manual refresh | About 1 s |
| Line-count refresh | Under 1 s |

## Handoff Notes

- Completed: Server log load, refresh, line-count, and wrapping behavior passed.
- Remaining unfinished coverage: None for ADM_08.
- Blocked or not applicable: None.
- State left for the next packet: Browser remains authenticated on Admin Server
  log with 50 lines and Wrap enabled; services remain healthy.
