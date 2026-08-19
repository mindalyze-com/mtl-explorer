# Packet: TRD_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_15
- In scope: Detail origin restoration from Statistics and Filter, browser history, narrow mobile, and direct-link Close fallback.
- Out of scope: Track browser feature completeness covered by FLT_20 and TBS packets.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01 and TRD_07.
- Required app/data state: Shared track browser with fifteen tracks.
- Required browser context: Authenticated desktop tab; temporary new tabs and responsive viewport capability allowed.

## Allowed Mutations

- Allowed: Search lists, navigate, use Back/Forward, and temporarily override viewport.
- Not allowed: Leave a viewport override or extra test tab active.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TRD_15 | Repeated direct-link Close and origin-aware return on the matching beta build at desktop and mobile sizes. | A direct link closes to the map; origin-aware return preserves its list state. | Direct-link Close returned to the map at both viewports, and origin-aware behavior remained intact. The earlier inert result did not reproduce. | REJECTED | [retest](../assets/TRD_15-retest.txt); [desktop](../assets/TRD_15-rejected-desktop.webp); [mobile](../assets/TRD_15-rejected-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-004 | P1 | Close is inert for a directly opened Track Details route. | In a fresh authenticated tab, navigate directly to `/mtl/track/100017` and activate the Track Details Close button by click or Enter. | Details close and the route returns to the map. | Route remains `/mtl/track/100017` and Track Details remains open. | [assets/TRD_15-origin-return.txt](../assets/TRD_15-origin-return.txt) | Direct/shared track URLs provide no working in-app Close fallback. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_15-origin-return.txt](../assets/TRD_15-origin-return.txt) | Origin, search-state, history, viewport, and fresh-direct-link observations. |

## Screenshot Evidence

Unavailable under ACC_04. Exact routes, detail identities, search values, result counts, and browser dimensions provide direct state evidence.

## Timings

| Step | Timing |
|---|---:|
| Statistics origin and history | About 35 s |
| Filter origin and history | About 35 s |
| Two narrow-viewport attempts and reset | About 4 s |
| Fresh direct-link Close retest | About 3 s |

## Handoff Notes

- Completed: Desktop origin preservation, desktop history, responsive capability attempts/reset, and fresh direct-link fallback.
- Remaining unfinished coverage: None for TRD_15; direct-link failure is terminal and tracked as FR-004.
- Blocked or not applicable: Narrow mobile requires a working viewport override; both existing and fresh tabs ignored the documented 390x844 setting.
- State left for the next packet: Main browser tab remains on track 100017; no viewport override or extra test tab remains.

## Remediation Verification

- Finding FR-004 is `REJECTED`: direct and origin-aware Close behavior passed in the matching beta build.
- No product change was made. Evidence is linked in the action row.
