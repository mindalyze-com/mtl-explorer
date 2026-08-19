# Packet: SYN_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: SYN_05
- In scope: Five-minute Dismiss snooze through a second server token change.
- Out of scope: Login behavior, covered by SYN_06.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_04.
- Required app/data state: In-sync 15-track client and one controlled synthetic source.
- Required browser context: Desktop Admin Maintenance.

## Allowed Mutations

- Allowed: Re-index only the controlled synthetic source with byte-distinct metadata.
- Not allowed: Reload during the snooze or alter geometry/track totals.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_05 | Dismissed a banner, performed a byte-distinct re-index during snooze, polled before/after five minutes, and kept the map open. | Banner stays hidden for five minutes through another token change and may reappear afterward if still stale. | Real invocation 2 replaced server track 100026→100027. Banner stayed hidden through 259 s, then reappeared at 320 s; cached 15-track map remained usable. | PASS | [assets/SYN_05-snooze.txt](../assets/SYN_05-snooze.txt); [assets/SYN_05-new-track-v2.gpx](../assets/SYN_05-new-track-v2.gpx); [assets/SYN_05-reappeared.jpg](../assets/SYN_05-reappeared.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_05-snooze.txt](../assets/SYN_05-snooze.txt) | Dismiss time, real re-index identity, poll checkpoints, and reappearance. |
| [assets/SYN_05-new-track-v2.gpx](../assets/SYN_05-new-track-v2.gpx) | Byte-distinct, geometry-equivalent controlled re-index source. |
| [assets/SYN_05-reappeared.jpg](../assets/SYN_05-reappeared.jpg) | Reappeared banner after the five-minute boundary. |

## Screenshot Evidence

- The full viewport captures the reappeared freshness banner over the still
  usable 15-track Admin/map context after the timed snooze.

## Timings

| Step | Timing |
|---|---:|
| Dismiss to real second re-index | 80 s |
| Last confirmed hidden poll | 259 s after Dismiss |
| Confirmed reappearance | 320 s after Dismiss |

## Handoff Notes

- Completed: Five-minute snooze and post-snooze reappearance passed with a real second token change.
- Remaining unfinished coverage: None for SYN_05.
- Blocked or not applicable: None.
- State left for the next packet: Client is intentionally stale with the banner
  visible; server has track 100027 and the client still has the equivalent cached track.
