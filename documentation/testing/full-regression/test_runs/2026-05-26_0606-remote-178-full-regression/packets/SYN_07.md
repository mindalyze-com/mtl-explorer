# Packet: SYN_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_07
- In scope: Indexer-running badge/state and map interaction during indexer activity.
- Out of scope: Long-running stress import.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_06
- Required app/data state: Signed in with 12 visible tracks.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Trigger a short media rescan and use map zoom.
- Not allowed: Add more import files for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_07 | Triggered `Rescan Media`, observed Jobs processing state, and used map zoom. | Indexer-running state surfaces as a badge and does not block map interaction. | Jobs tile showed `PROCESSING`; the media rescan queued/settled; map Zoom in remained responsive and changed scale from 1000 km to 500 km. | PASS | `assets/SYN_07-indexer-running.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SYN_07-indexer-running.txt | Processing badge/state and map interaction evidence. |

## Timings

| Step | Timing |
|---|---:|
| Rescan and zoom interaction | <2 min |

## Handoff Notes

- Completed: SYN_07 passed.
- Remaining unfinished coverage: APP_01 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Admin Jobs panel is open; map is at 500 km scale; client shows 12 visible tracks.
