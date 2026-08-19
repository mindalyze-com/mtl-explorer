# Packet: MED_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_02
- In scope: Viewport-scoped media loading on pan/zoom.
- Out of scope: Viewer behavior.

## Prerequisites

- Required previous coverage IDs or run packets: MED_01.
- Required app/data state: Media enabled; six Bern media points.
- Required browser context: Main map across world, Bern, and New York viewports.

## Allowed Mutations

- Allowed: Pan/zoom and location search.
- Not allowed: Media changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_02 | Loaded the overview, moved to Bern, then moved to New York while recording media-bounds requests on the fixed build at desktop and mobile sizes. | Media loads for useful current viewports and does not cache a global request. | The overview issued no global request. Bern and New York each issued distinct bounded requests and updated the overlay at both viewports. | FIXED | [details](../assets/MED_02-remediation.txt); [desktop](../assets/MED_02-fixed-desktop.webp); [mobile](../assets/MED_02-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-009 | P1 | Media overlay loads/retains an initial world-scale bound instead of requesting local viewports. | Reload with media enabled at the initial 500 km view, search Bern, pan/zoom, then search New York while observing media-bounds requests. | Each uncovered current viewport causes a bounded request; no whole-world library load. | Only the initial broad request was logged; both local 100 m destinations reused it and issued no new media-bounds request. | [assets/MED_02-viewport-loading.txt](../assets/MED_02-viewport-loading.txt) | Large libraries can be fetched at startup instead of being bounded by the local viewport. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_02-viewport-loading.txt](../assets/MED_02-viewport-loading.txt) | View changes, request audit, endpoint controls, and finding. |

## Screenshot Evidence

Unavailable under ACC_04. Request timing plus exact map scales and endpoint controls provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Bern pan/zoom | About 5 s |
| New York jump and request audit | About 4 s |

## Handoff Notes

- Completed: Viewport loading checked; FR-009 recorded.
- Remaining unfinished coverage: None for MED_02.
- Blocked or not applicable: None.
- State left for the next packet: Main map at New York; media enabled.

## Remediation Verification

- Finding FR-009 is `FIXED`: broad overview bounds are skipped and cannot suppress later local loads.
- Request-generation guards also prevent an older response from replacing a newer viewport result.
