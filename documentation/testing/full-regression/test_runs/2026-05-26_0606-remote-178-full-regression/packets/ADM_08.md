# Packet: ADM_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_08
- In scope: Server log loading and refresh.
- Out of scope: Deep log search/filtering.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_07
- Required app/data state: Admin workspace available.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open Server Log and refresh it.
- Not allowed: Alter server logging configuration.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_08 | Opened Admin -> Log and clicked Refresh. | Server log lines load and refresh. | Timestamped server log lines loaded; Refresh added/advanced visible API request entries. | PASS | `assets/ADM_08-server-log.webp`, `assets/ADM_08-server-log.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_08-server-log.webp | Screenshot of Server Log panel after refresh. |
| assets/ADM_08-server-log.txt | Compact summary of representative loaded/refreshed log lines. |

## Timings

| Step | Timing |
|---|---:|
| Open log and refresh | <1 min |

## Handoff Notes

- Completed: ADM_08 passed.
- Remaining unfinished coverage: ADM_09 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Log panel is open; freshness banner remains visible.
