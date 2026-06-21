# Packet: PLN_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_09
- In scope: Missing-routing-data frontend behavior for planner route requests.
- Out of scope: Real sidecar segment download speed and backend BRouter coverage.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_01, PLN_02
- Required app/data state: Planner UI available.
- Required browser context: Authenticated desktop browser context against `http://178.104.209.132:18080/mtl/`.

## Allowed Mutations

- Allowed: Browser-level route API interception and transient unsaved planner waypoints.
- Not allowed: Save planned routes or mutate imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_09 | Intercepted `/api/planner/route` with HTTP 503 JSON `{error:"segment-downloading", detail:"Downloading routing data for this area..."}`, then placed two waypoints. | UI shows a clear segment-downloading/unavailable message instead of an unhandled error; no route is saved or drawn as successful. | PASS. Planner displayed `Downloading routing data for this area... (auto-retry 1/6)`, kept stats at `0.00 km / 0 legs`, Save route stayed disabled, and no page error fired. | PASS | [assets/PLN_09-segment-downloading.txt](../assets/PLN_09-segment-downloading.txt); [assets/PLN_09-segment-downloading.webp](../assets/PLN_09-segment-downloading.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| PLN-09-P3 | P3 | Clearing a failed planner route leaves the stale segment-downloading notice visible. | With a route request returning `segment-downloading`, click Clear route after the notice appears. | Clearing the route also clears the route error/notice. | FIXED locally: planner `clearAll()` now clears pending route errors, segment retry/debounce timers, stale route work, and computing state. | [assets/PLN_09-segment-downloading.txt](../assets/PLN_09-segment-downloading.txt); [assets/FIXED-filter-planner-local-verification.txt](../assets/FIXED-filter-planner-local-verification.txt) | FIXED locally; full browser regression was not rerun after the code change. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/PLN_09-segment-downloading.txt](../assets/PLN_09-segment-downloading.txt) | Intercepted response, route request count, UI snapshots, and stale-notice issue evidence. |
| [assets/PLN_09-segment-downloading.webp](../assets/PLN_09-segment-downloading.webp) | Planner showing the segment-downloading auto-retry notice. |
| [assets/FIXED-filter-planner-local-verification.txt](../assets/FIXED-filter-planner-local-verification.txt) | Local implementation and focused test evidence for clearing stale planner route notices. |

## Screenshot Evidence

![PLN_09 segment downloading notice](../assets/PLN_09-segment-downloading.webp)

## Timings

| Step | Timing |
|---|---:|
| Planner zoom/setup | 6 zoom clicks until planning enabled |
| Missing-data route handling | 1 intercepted route request |

## Handoff Notes

- Completed: PLN_09 passed for clear missing-data messaging without an unhandled page error; PLN-09-P3 is `FIXED` locally.
- Remaining unfinished coverage: PLN_10 and later coverage IDs remain queued.
- Blocked or not applicable: None for PLN_09.
- State left for the next packet: Transient planner waypoints were cleared; a fresh browser context is recommended to avoid carrying the stale notice.
