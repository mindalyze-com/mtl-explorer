# Packet: ADM_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ADM_06
- In scope: Operational status for map tiles, location search, and routing segments.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_05.
- Required app/data state: Configured quick-install helper services healthy.
- Required browser context: Admin Processing.

## Allowed Mutations

- Allowed: Observe/refresh operational task status only.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_06 | Inspected all Map & Routing cards and their detail text. | Vector tiles, location search, and routing expose ready/downloading/unavailable/disabled states with useful detail. | All three applicable services were ready/done with explicit progress, provider/version/build/source/count detail. Non-ready variants were not applicable on this healthy target. | PASS | [assets/ADM_06-operational-tasks.txt](../assets/ADM_06-operational-tasks.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_06-operational-tasks.txt](../assets/ADM_06-operational-tasks.txt) | Exact ready states and technical detail for all services. |

## Screenshot Evidence

Live desktop inspection confirmed the three operational cards. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Status refresh/render | Under 1 s |

## Handoff Notes

- Completed: All configured operational services and detail.
- Remaining unfinished coverage: None for ADM_06.
- Blocked or not applicable: Downloading/unavailable/disabled variants were not applicable; screenshots blocked.
- State left for the next packet: Services ready; maintenance freshness banner may still be present.
