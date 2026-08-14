# Packet: TBS_08

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: TBS_08.
- In scope: Statistics updates across the required five-import and two-delete timeline.
- Out of scope: Filter's separate stale-delete behavior.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_07, IMP_05, and DEL_03.
- Required app/data state: preserved lifecycle evidence from the same frozen run and requested image.
- Required browser context: Statistics Overview and Tracks before/after supported freshness Reload.

## Allowed Mutations

- Allowed: assemble this packet from the already completed lifecycle packets.
- Not allowed: repeat imports/deletes or substitute the later local fix image.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_08 | Compared Statistics after the five-file import and again after deleting two imported tracks. | Statistics updates at both transitions; deleted totals do not remain. | Delete transition passed at three tracks with deleted-name searches empty. Import freshness Reload left Overview at 0/5 until a normal browser reload. | FAIL | [timeline](../assets/TBS_08-import-delete-statistics.txt), [import result](../assets/IMP_05-reload-result.txt), [delete result](../assets/DEL_03-cross-surface.txt), [recovered import Stats](../assets/IMP_05-stats-after-normal-reload.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| IMP-05-P1 | P1 | Freshness Reload leaves Statistics Overview empty after the initial five-track import. | Start with an open empty install; import five GPX files; wait for all jobs; use New data available → Reload; open Overview. | Overview immediately shows the same five tracks as map and Track Browser. | Overview shows 0 of 5 until a normal browser reload, although the later 5→3 delete update reaches Statistics correctly. | [import result](../assets/IMP_05-reload-result.txt), [timeline](../assets/TBS_08-import-delete-statistics.txt) | Users see empty core statistics after the documented import-refresh flow. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_08-import-delete-statistics.txt](../assets/TBS_08-import-delete-statistics.txt) | Consolidated Statistics lifecycle result. |
| [assets/IMP_05-reload-result.txt](../assets/IMP_05-reload-result.txt) | Original five-import freshness failure and recovery. |
| [assets/DEL_03-cross-surface.txt](../assets/DEL_03-cross-surface.txt) | Original two-delete Statistics result. |
| [assets/IMP_05-stats-after-normal-reload.webp](../assets/IMP_05-stats-after-normal-reload.webp) | Five-track Overview only after normal reload. |

## Screenshot Evidence

The recovered five-track Overview screenshot is paired with exact pre-recovery and deletion state logs.

## Timings

| Step | Timing |
|---|---:|
| Import freshness check | 4 s after helper Reload |
| Normal reload recovery | 3 s |
| Delete freshness verification | 3 min cross-surface check |

## Handoff Notes

- Completed: TBS_08 is terminal `FAIL` under existing IMP-05-P1.
- Remaining unfinished coverage: TBS_09 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: current browser unchanged; desktop Filter open with all categories and twelve tracks.
