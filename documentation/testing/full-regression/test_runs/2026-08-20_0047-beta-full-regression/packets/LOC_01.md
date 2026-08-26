# Packet: LOC_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_01
- In scope: Expected locale formatting for numbers, distances, durations, and dates.

## Prerequisites

- Required previous coverage IDs or run packets: APP_08.
- Required app/data state: en-GB detected locale; Metric units; populated statistics.
- Required browser context: Admin Preferences and Statistics Overview.

## Allowed Mutations

- Allowed: Navigate only; no locale/unit change in this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_01 | Compared Preferences examples with live Statistics totals, durations, distances, and activity/milestone dates. | Values render in the expected locale format. | en-GB day/month dates, comma grouping, metric scaling, and duration notation were consistent across preview and live data. | PASS | [assets/LOC_01-locale-format.txt](../assets/LOC_01-locale-format.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC_01-locale-format.txt](../assets/LOC_01-locale-format.txt) | Preference previews and representative live values. |

## Screenshot Evidence

Direct formatted-text evidence is durable; ACC_04 prevents saved screenshots.

## Timings

| Step | Timing |
|---|---:|
| Preferences/Statistics comparison | About 17 s |

## Handoff Notes

- Completed: en-GB numbers, units, durations, and dates.
- Remaining unfinished coverage: None for LOC_01.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Statistics Overview open; en-GB/Metric selected.
