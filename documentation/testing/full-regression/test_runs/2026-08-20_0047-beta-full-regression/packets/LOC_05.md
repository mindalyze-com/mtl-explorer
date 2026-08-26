# Packet: LOC_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_05
- In scope: Metric/imperial conversion and persistence across representative detailed surfaces.
- Out of scope: Changing underlying track, route, or segment data.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_04 and the same-run map, segment, planner, replay, filter, and detail packets.
- Required app/data state: Eight tracks; #100039 restored; Smart Base Filter available.
- Required browser context: Authenticated desktop browser; de-DE locale.

## Allowed Mutations

- Allowed: Change the local measurement preference; temporarily set distance-filter parameters; reload; reset the filter.
- Not allowed: Edit source track, route, or segment data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_05 | Compared Metric and Imperial Preferences, Statistics, Track Browser/Details/graphs, point popup, distance filter, segment, Planner, and replay values; reloaded in both modes and restored Metric. Retested the distance-filter summary on the current-worktree local stack at desktop and 390x760 sizes. | Distance/elevation/speed/vertical-rate/weight convert consistently; dates, duration, watts, and Wh do not; persisted settings do not mix units. | Core values convert and persist correctly. The fixed filter summary now renders `10 mi` in Imperial and the same canonical value as localized `16,09 km` in de-DE Metric. | FIXED | [assets/LOC_05-unit-conversion.txt](../assets/LOC_05-unit-conversion.txt); [assets/MTL-FR-022-fix-local.txt](../assets/MTL-FR-022-fix-local.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Finding status | Release impact |
|---|---|---|---|---|---|---|---|---|
| MTL-FR-022 | P2 | Distance-filter summary leaks a raw internal metric value without a unit. | Select Imperial (US), choose Tracks by distance (gradient), set maximum to 10 mi, and apply. | Summary is localized and unit-aware, such as 10 mi. | Fixed locally: the summary shows `10 mi` in Imperial and `16,09 km` in de-DE Metric, including after the preference changes while the filter remains active. | [assets/MTL-FR-022-fix-local.txt](../assets/MTL-FR-022-fix-local.txt) | FIXED | No remaining release impact in the verified local flow. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC_05-unit-conversion.txt](../assets/LOC_05-unit-conversion.txt) | Paired unit values, reload persistence, point popup, filter defect, and restoration. |
| [assets/MAP_11-point-popup.txt](../assets/MAP_11-point-popup.txt) | Same-run metric point-popup values. |
| [assets/MCT_02-open-result.txt](../assets/MCT_02-open-result.txt) | Same-run metric segment value. |
| [assets/PLN_03-insert-waypoint.txt](../assets/PLN_03-insert-waypoint.txt) | Same-run metric planner route values. |
| [assets/MTL-FR-022-fix-local.txt](../assets/MTL-FR-022-fix-local.txt) | Root cause, implementation, focused checks, and local desktop/mobile retest. |

## Screenshot Evidence

| View | Evidence |
|---|---|
| Desktop | [assets/MTL-FR-022-fix-local-desktop.webp](../assets/MTL-FR-022-fix-local-desktop.webp) |
| Mobile 390x760 | [assets/MTL-FR-022-fix-local-mobile.webp](../assets/MTL-FR-022-fix-local-mobile.webp) |

## Timings

| Step | Timing |
|---|---:|
| Unit switch | About 0.5 s |
| Replay telemetry population | About 1.6 s |
| Each persistence reload | About 1.5 s |

## Handoff Notes

- Completed: Paired representative conversions, imperial persistence, metric restoration, filter reset, and the fixed distance-summary retest at desktop and mobile sizes.
- Remaining unfinished coverage: None for LOC_05.
- Blocked or not applicable: None for the fixed distance-summary path.
- State left for the next packet: de-DE/Metric; Smart Base Filter; 8 tracks; root map open.

## Fix Record

- Root cause: active filter identity appended numeric string parameters without applying effective UI unit metadata, saved measurement preference, or locale formatting.
- Source: `mtl-client/src/utils/activeFilterIdentity.ts` and `mtl-client/src/utils/filterParamUnits.ts`.
- Tests: focused identity, parameter-unit, and reactive filter-store coverage; 41 focused tests passed with scoped lint, type, and format checks.
- UI proof: the current-worktree local stack renders `10 mi` in Imperial and `16,09 km` in de-DE Metric at desktop and 390x760 sizes.
