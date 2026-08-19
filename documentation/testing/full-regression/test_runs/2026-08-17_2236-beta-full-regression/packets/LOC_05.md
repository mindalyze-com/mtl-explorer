# Packet: LOC_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_05
- In scope: Metric/Imperial conversion, consistency, persistence, and restoration across all named surfaces.
- Out of scope: Mutating source tracks or saved production data.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_04.
- Required app/data state: 15-track controlled map; track 100005, track 100017 point 3, Bern segment crossings, and BRouter available.
- Required browser context: Authenticated desktop session.

## Allowed Mutations

- Allowed: Unit preference, temporary filter values, segment zones, one temporary saved Planner route, reloads, and reversible map-layer visibility.
- Not allowed: Change an underlying track, route geometry, or source file.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_05 | Switched to Imperial and inspected main, 3D, and mini-map scales before and after reload on the fixed build at desktop and mobile sizes. | Every map scale follows the measurement preference and updates live. | The main scale rendered miles at both viewports; all scale controls share the same metric/imperial unit helper and update on preference changes. Metric restoration also passed. | FIXED | [details](../assets/LOC_05-remediation.txt); [desktop](../assets/LOC_05-fixed-desktop.webp); [mobile](../assets/LOC_05-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-014 | P1 | Imperial preference leaves the global map scale in metric units. | In Admin → Preferences choose Imperial (US), then open Statistics, Tracks, Track Details/point popup, Segments, Planner, or replay and compare the map scale with adjacent values. | The map scale converts to feet/miles and no checked view mixes unit systems. | Adjacent content converts to mi/ft/mph, but the map scale remains `500 m` or `1 km`. | [assets/LOC_05-units-results.txt](../assets/LOC_05-units-results.txt); [assets/LOC_05-imperial-planner.jpg](../assets/LOC_05-imperial-planner.jpg); [assets/LOC_05-imperial-point.jpg](../assets/LOC_05-imperial-point.jpg) | Imperial users see conflicting distance systems on every map-backed surface. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC_05-units-results.txt](../assets/LOC_05-units-results.txt) | Exact paired values, persistence, restore, cleanup, and FR-014 scope. |
| [assets/LOC_05-metric-preferences.jpg](../assets/LOC_05-metric-preferences.jpg) | Metric preference and preview. |
| [assets/LOC_05-imperial-preferences.jpg](../assets/LOC_05-imperial-preferences.jpg) | Persisted Imperial preference and mi/ft/lb preview. |
| [assets/LOC_05-imperial-planner.jpg](../assets/LOC_05-imperial-planner.jpg) | Same saved route in mi/ft, with the incorrect metric map scale visible. |
| [assets/LOC_05-imperial-point.jpg](../assets/LOC_05-imperial-point.jpg) | Same point 3 in ft/mph, with the incorrect metric map scale visible. |

## Screenshot Evidence

- Paired Preferences screenshots show Metric and Imperial selections/previews.
- Imperial Planner and point screenshots show correct converted content and the remaining metric scale that triggers FR-014.

## Timings

| Step | Timing |
|---|---:|
| Live surface conversions | Under 500 ms each |
| Imperial persistence reload | 1.9 seconds |
| Metric restoration reload | 1.8 seconds |

## Handoff Notes

- Completed: All named conversion surfaces, persistence, restoration, and cleanup were exercised.
- Remaining unfinished coverage: None for LOC_05.
- Blocked or not applicable: None.
- State left for the next packet: Browser-default en-GB/Metric behavior, clean filter, default map settings, no temporary saved plan, 15 tracks.

## Remediation Verification

- Finding FR-014 is `FIXED`: MapLibre scale controls now initialize and react to the selected measurement system.
- Automated coverage includes unit mapping and 3D wiring; browser checks covered live main-map behavior and reload.
