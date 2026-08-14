# Packet: FLT_12

> **FIX FOLLOW-UP — 2026-08-14: FIXED AND VERIFIED.** The original beta failure below is retained as run history. See [follow-up evidence](../fix-verification.md#resolution-matrix).

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: FLT_12.
- In scope: select-none stability and select-every normalization to All categories.
- Out of scope: unavailable selected category display, covered next.

## Prerequisites

- Required previous coverage IDs or run packets: FLT_11.
- Required app/data state: 2010 and 2013 are the only available categories in range.
- Required browser context: Included categories and map result.

## Allowed Mutations

- Allowed: Clear selection, reload, Select current, and Apply.
- Not allowed: introduce unavailable categories during the all-selection check.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| FLT_12 | Cleared all categories and reloaded the zero result; then selected both available categories and reopened selection state. | Empty result is stable; every available category normalizes to All categories when no saved category is unavailable. | Empty state passed and survived reload. Selecting both categories restored eight tracks but remained an exact 2-of-2 set with the All categories master unchecked. | FAIL | [none and all](../assets/FLT_12-none-and-all.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FLT-12-P2 | P2 | Selecting every available result category does not normalize to All categories. | With only 2010/2013 available, Clear selection; Select current; Apply; reopen categories. | All categories is active, allowing future categories automatically. | Both rows are checked but All categories remains unchecked; overview says 2 of 2. | [none and all](../assets/FLT_12-none-and-all.txt) | Future discovered categories stay unexpectedly excluded from an apparently complete selection. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FLT_12-none-and-all.txt](../assets/FLT_12-none-and-all.txt) | Zero/reload state and exact checkbox state after selecting every available category. |

## Screenshot Evidence

Exact checkbox properties and current-result values provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Empty apply | < 1 s |
| Empty reload | < 2 s |
| Every-category apply | < 1 s |

## Handoff Notes

- Completed: FLT_12 is terminal `FAIL` with FLT-12-P2.
- Remaining unfinished coverage: FLT_13 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: Included categories open; 2010 and 2013 checked exactly; All categories unchecked; year range 2010-2013; result 8/12.
