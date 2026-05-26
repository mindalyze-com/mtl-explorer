# Packet: ADM_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_07
- In scope: Data freshness status, timestamps/tokens, and reload availability.
- Out of scope: Applying the reload; that remains for SYN_02.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_06
- Required app/data state: ADM_02 upload/rescans produced a visible freshness banner.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open Freshness panel and inspect status.
- Not allowed: Click the data-reload action in this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_07 | Opened Admin -> Freshness and inspected the existing data freshness banner. | Shows last-update timestamp and offers reload. | Banner offered `Reload` and `Dismiss`; Freshness panel showed `Out of sync`, checked time, latest change timestamp, server/client tokens, outdated domains, and healthy polling. | PASS | `assets/ADM_07-freshness-panel.webp`, `assets/ADM_07-freshness.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/ADM_07-freshness-panel.webp | Screenshot of the Freshness panel and reload banner. |
| assets/ADM_07-freshness.txt | Compact freshness tokens/timestamps and banner actions. |

## Timings

| Step | Timing |
|---|---:|
| Inspect Freshness panel | <1 min |

## Handoff Notes

- Completed: ADM_07 passed.
- Remaining unfinished coverage: ADM_08 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Freshness panel is open; data reload banner remains unclicked for SYN coverage.
