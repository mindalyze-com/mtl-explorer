# Packet: SYN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_05
- In scope: Five-minute Dismiss snooze across a further server token change and polling cycles.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_02 synchronized client.
- Required app/data state: Ready GPS indexer; stable underlying 8-track dataset.
- Required browser context: Signed-in main map.

## Allowed Mutations

- Allowed: Trigger two GPS rescans; dismiss one banner; wait through the full snooze boundary.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_05 | Dismissed a freshness banner, changed the server token again, sampled five polling windows, and crossed five minutes. | Banner stays hidden for five minutes despite further changes and may reappear afterward. | Hidden through 242.958 s; reappeared at 300.520 s with the pending revision and normal actions. | PASS | [assets/SYN_05-snooze.txt](../assets/SYN_05-snooze.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_05-snooze.txt](../assets/SYN_05-snooze.txt) | Exact dismissal time and sampled elapsed states through expiry. |

## Screenshot Evidence

Live desktop inspection confirmed each sampled state. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Initial banner | 0.5 s |
| Snooze expiry/reappearance | 300.520 s |

## Handoff Notes

- Completed: Full five-minute snooze across another token change and reappearance.
- Remaining unfinished coverage: None for SYN_05.
- Blocked or not applicable: Durable screenshots only.
- State left for the next packet: Freshness banner visible again over the usable 8-track map.
