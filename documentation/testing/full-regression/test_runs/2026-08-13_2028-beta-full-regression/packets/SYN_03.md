# Packet: SYN_03

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: SYN_03.
- In scope: aggregate result of the executed five-GPX import and delete-two flow.

## Prerequisites

- Required previous coverage IDs or run packets: IMP_01–IMP_09 and DEL_01–DEL_05.
- Required app/data state: exact public five-file flow already executed in queue order.
- Required browser context: watcher, map, Filter, Statistics, heatmap, and details evidence from those packets.

## Allowed Mutations

- Allowed: none; consolidate the direct executed results.
- Not allowed: replace the frozen flow with later data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_03 | Reconciled every executed import/delete packet against the aggregate frozen requirement. | Indexer, banner, map, browser, stats, filters, heatmap, and details all follow the five-import/delete-two source of truth through freshness Reload. | Watcher and final surfaces were correct, but both transitions were inconsistent after freshness Reload: initial import left Filter/Stats at zero and deletion left removed Filter rows until a normal browser reload. | FAIL | [flow summary](../assets/SYN_03-flow.txt), [import failure](IMP_05.md), [delete failure](DEL_03.md), [final delete state](DEL_05.md) |

## Issues

- Reuses `IMP-05-P1` and `DEL-03-P1`; no duplicate issue was created.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_03-flow.txt](../assets/SYN_03-flow.txt) | Exact aggregate import/delete outcome. |
| [packets/IMP_05.md](IMP_05.md) | Initial-import cache failure. |
| [packets/DEL_03.md](DEL_03.md) | Delete cache failure. |
| [packets/DEL_05.md](DEL_05.md) | Eventual final source-of-truth state. |

## Screenshot Evidence

See the linked import/delete packet screenshots; this aggregate packet adds no duplicate screenshot.

## Timings

| Step | Timing |
|---|---:|
| Aggregate reconciliation | < 1 min |

## Handoff Notes

- Completed: SYN_03 is terminal `FAIL`.
- Remaining unfinished coverage: SYN_04 onward.
- Blocked or not applicable: none; failures were directly reproduced.
- State left for the next packet: Q1 Statistics at the restored 12-track regression baseline.
