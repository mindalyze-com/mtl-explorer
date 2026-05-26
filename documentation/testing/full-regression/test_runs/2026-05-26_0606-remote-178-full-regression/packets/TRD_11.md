# Packet: TRD_11

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: TRD_11
- In scope: energy what-if recalculation controls.
- Out of scope: persisted activity type changes.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_02.
- Required app/data state: track #100001 detail Overview open.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: adjust temporary what-if controls if visible.
- Not allowed: persist metadata changes.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_11 | Opened the energy section and `About energy` dialog, then scanned for weight/mass what-if controls. | Custom rider weight or equivalent what-if control updates displayed energy without permanently saving. | The Overview showed energy values and mass used, but the About dialog only contained explanatory text; no rider-weight/what-if input was exposed. | FAIL | `assets/TRD_11-energy-about-open.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| TRD-03 | P3 | Energy what-if recalculation control is not exposed in tested track details. | Open track #100001 Overview and click `About energy`. | A temporary rider-weight/what-if control is available and updates displayed energy. | Only explanatory text appears; no input/control is visible. | `assets/TRD_11-energy-about-open.webp` | Users cannot test energy sensitivity from track details. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/TRD_11-energy-about-open.webp | Energy section and About dialog. |

## Timings

| Step | Timing |
|---|---:|
| Energy control scan | <1m |

## Handoff Notes

- Completed: TRD_11 terminal FAIL.
- Remaining unfinished coverage: continue with TRD_12.
- Blocked or not applicable: none.
- State left for the next packet: no data changed.
