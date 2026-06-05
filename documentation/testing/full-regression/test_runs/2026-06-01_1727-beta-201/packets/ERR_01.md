# Packet: ERR_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ERR_01
- In scope: Failed track load, failed map/config load, failed media applicability, failed planner route, and expired session recovery behavior.
- Out of scope: Rapid tool switching cleanup; covered by ERR_02.

## Prerequisites

- Required previous coverage IDs or run packets: NET_02, NET_03, NET_04, MED_05, PLN_09.
- Required app/data state: Authenticated app with 12 visible tracks.
- Required browser context: Isolated desktop Chromium contexts for simulated failures.

## Allowed Mutations

- Allowed: Abort selected API requests in isolated contexts and corrupt isolated JWTs.
- Not allowed: Stop the target server or corrupt shared data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ERR_01 | Aggregated direct error-recovery evidence and added a client-side failed track-detail load simulation by aborting track `100000` detail/chart/related APIs. Retested locally after the client fix with the same aborted track-detail endpoints. | Failed track load, failed map config, failed media, failed planner route, and expired session each show an actionable message rather than freezing or going blank. | Fixed: network/map failure showed Retry; planner route trouble showed retry/auto-retry notice; expired token redirected to login; failed media was not applicable with zero media records. Retesting the failed track-detail load on 2026-06-04 showed `Track details could not be loaded` with Retry and Back to map instead of a bare details shell. | FIXED | [assets/ERR_01-error-recovery.txt](../assets/ERR_01-error-recovery.txt); [assets/ERR_01-failed-track-load.txt](../assets/ERR_01-failed-track-load.txt); [assets/ERR_01-failed-track-load.webp](../assets/ERR_01-failed-track-load.webp); [assets/ERR_01-failed-track-load-fixed.txt](../assets/ERR_01-failed-track-load-fixed.txt); [assets/ERR_01-failed-track-load-fixed.webp](../assets/ERR_01-failed-track-load-fixed.webp); [assets/NET_02-network-recovery.txt](../assets/NET_02-network-recovery.txt); [assets/NET_03-auth-redirect.txt](../assets/NET_03-auth-redirect.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MTL-FR-007 | P2 | Failed track-detail API load now shows actionable recovery. | In an authenticated client shell, route to `/mtl/track/100000` while aborting `/api/tracks/get/100000`, `/api/tracks/get/100000/details`, `/api/tracks/100000/chart-series`, and `/api/tracks/related/100000`. | Track details should show an actionable error with retry/back/reload guidance when required detail APIs fail. | Fixed: the panel shows `Track details could not be loaded` with Retry and Back to map; the bare tabs-only shell no longer appears. | [assets/ERR_01-failed-track-load-fixed.txt](../assets/ERR_01-failed-track-load-fixed.txt); [assets/ERR_01-failed-track-load-fixed.webp](../assets/ERR_01-failed-track-load-fixed.webp) | FIXED |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ERR_01-error-recovery.txt](../assets/ERR_01-error-recovery.txt) | Aggregate error-case summary and decision. |
| [assets/ERR_01-failed-track-load.txt](../assets/ERR_01-failed-track-load.txt) | Aborted track-detail APIs and resulting non-actionable UI state. |
| [assets/ERR_01-failed-track-load.webp](../assets/ERR_01-failed-track-load.webp) | Bare track-details shell after API failures. |
| [assets/ERR_01-failed-track-load-fixed.txt](../assets/ERR_01-failed-track-load-fixed.txt) | 2026-06-04 local retest showing the same aborted APIs now produce an actionable recovery state. |
| [assets/ERR_01-failed-track-load-fixed.webp](../assets/ERR_01-failed-track-load-fixed.webp) | Fixed track-detail failure state with Retry and Back to map. |
| [assets/NET_02-network-recovery.txt](../assets/NET_02-network-recovery.txt) | Failed map/config/track-load recovery state with Retry. |
| [assets/NET_03-auth-redirect.txt](../assets/NET_03-auth-redirect.txt) | Expired-token redirect to login. |
| [assets/PLN_desktop-flow.txt](../assets/PLN_desktop-flow.txt) | Planner route trouble retry/auto-retry notice. |
| [assets/MED_media-availability.txt](../assets/MED_media-availability.txt) | Media error case applicability evidence. |

## Screenshot Evidence

**Bare track-details shell after API failures.**

![Bare track-details shell after API failures.](../assets/ERR_01-failed-track-load.webp)

**Fixed track-detail failure state with Retry and Back to map.**

![Fixed track-detail failure state with Retry and Back to map.](../assets/ERR_01-failed-track-load-fixed.webp)

## Timings

| Step | Timing |
|---|---:|
| Aggregate review plus failed track-detail simulation | ~5 min |

## Handoff Notes

- Completed: ERR_01 retested and terminal as `FIXED`.
- Remaining unfinished coverage: Continue with ERR_02.
- Blocked or not applicable: Failed media is not applicable because this run has zero indexed media.
- State left for the next packet: Isolated failure contexts closed; server state unchanged.
