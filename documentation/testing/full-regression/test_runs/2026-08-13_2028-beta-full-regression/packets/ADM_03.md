# Packet: ADM_03

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: ADM_03.
- In scope: GPS/media indexer pending, running, completed, failed, removed, and refresh behavior.

## Prerequisites

- Required previous coverage IDs or run packets: ADM_02.
- Required app/data state: completed media/GPS indexes; disposable invalid GPX may be added.
- Required browser context: Admin Processing.

## Allowed Mutations

- Allowed: add one named invalid synthetic GPX to create a real failure.
- Not allowed: alter non-disposable sources.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_03 | Observed GPS/media status, refreshed it over time, then added an invalid synthetic GPX and compared Admin with the authoritative database result. | Pending/running/completed/failed/removed states are shown and refresh updates over time. | Refresh and automatic polling changed the timestamp, media/GPS completed state was shown, and GPS retained `2 removed`. The invalid track reached `FAILED` in the database, but Admin increased `completed` from 15 to 16 and exposed no failed state. | FAIL | [UI](../assets/ADM_03-failed-hidden.webp), [status comparison](../assets/ADM_03-status.txt) |

## Issues

### ADM-03-P1 — Failed GPS imports are reported as completed

- Severity: P1
- Expected: the failed aggregate and state identify an invalid indexed file.
- Actual: a track with database `load_status=FAILED` increased Admin's completed count; no failed count appeared.
- Impact: operators can believe ingestion is healthy while a source failed to load.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_03-failed-hidden.webp](../assets/ADM_03-failed-hidden.webp) | Admin GPS status after the real failed import. |
| [assets/ADM_03-status.txt](../assets/ADM_03-status.txt) | UI refresh sequence and database comparison. |

## Screenshot Evidence

![Failed file hidden in completed count](../assets/ADM_03-failed-hidden.webp)

## Timings

| Step | Timing |
|---|---:|
| Invalid-file watcher detection | < 2 s |
| Admin refresh | < 0.4 s |

## Handoff Notes

- Completed: ADM_03 is terminal `FAIL`.
- Remaining unfinished coverage: ADM_04 onward.
- Blocked or not applicable: failed/pending/running index states are not represented truthfully by the terminal aggregate UI.
- State left for the next packet: one synthetic failed file remains for controlled removal; freshness banner visible.
