# Packet: MED_42

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_42
- In scope: One active conversion, one retained completion, global/session byte caps, maximum runtime, disconnect grace, completed expiry, oldest-completed eviction, startup orphan cleanup, active-output protection, user recovery, and before/during/after process/session/disk counts.
- Out of scope: The 480p/720p decoder outcome already owned by MED_41.

## Prerequisites

- Required previous coverage IDs or run packets: MED_41 and its temporary archival sources.
- Required app/data state: Indexed 8 s archival media plus a disposable 80 s stream-copy loop; normal media baseline recorded for restoration.
- Required browser context: Authenticated production CompatibleVideoPlayer harness using a naturally unsupported source; no simulated media error.

## Allowed Mutations

- Allowed: Restart only the app surface with short test-only transcode properties, seed exact temporary orphan/filler files, create/poll/cancel sessions, and delete all disposable fixtures afterward.
- Not allowed: Change database/support services, requested image, frozen queue, production code, or private media.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_42 | Ran isolated short-limit phases for startup cleanup, active/concurrent capacity, completion retention/eviction/expiry, disconnect/runtime termination, per-session/global bytes, active protection, visible Retry/Cancel, then restored the normal app and baseline. | Limits remain bounded; active output is never evicted; hard-limit crossings stop ffmpeg, remove session output, and provide a recoverable message; startup/expiry/cleanup leave no orphan work. | Every branch matched. Concurrent work returned 429; completed count stayed at one; expiry/orphan cleanup removed files; disconnect/runtime/per-session caps stopped ffmpeg and removed directories; the UI exposed exact failure plus Retry/Cancel; global pressure evicted completed output while the active session continued to completion. Final normal-app state is clean at 8/8/8 media and 0/0 queues. | PASS | [assets/MED_42-session-limits.txt](../assets/MED_42-session-limits.txt) |

## Issues

No new issue. The 720p decode finding remains owned by MED_41/MTL-FR-019; MED_42's bounded lifecycle and recovery controls behaved as expected.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_42-session-limits.txt](../assets/MED_42-session-limits.txt) | Test-only limits, session/process/disk counts, terminal messages, eviction/expiry/orphan behavior, active protection, and cleanup restoration. |

## Screenshot Evidence

Live desktop inspection confirmed Preparing/progress/Cancel, the exact hard-limit failure and Retry, fresh retry, and cancelled cleanup states. ACC_04 prevents durable local screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Long P720 completion | About 4 s |
| Disconnect/runtime terminal checks | 2 s each |
| Hard-cap monitor cleanup | Under 2 s |
| Completed expiry observation | 6 s wait with 4 s limit |

## Handoff Notes

- Completed: Every MED_42 lifecycle/cap branch, user recovery, active-output protection, and temporary fixture/service/harness cleanup.
- Remaining unfinished coverage: None for MED_42.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Normal Compose app restored on requested image/ID; HTTP 200; media/resolved/selected 8/8/8; queues 0/0; transcode sessions/processes 0/0; no MED_41/42 fixture or harness remains.
