# Packet: MCT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MCT_03
- In scope: stopping Segment Analyzer and verifying temporary UI cleanup.
- Out of scope: compare/trends modes.

## Prerequisites

- Required previous coverage IDs or run packets: MCT_01, MCT_02.
- Required app/data state: Segment Analyzer active with A/B zones.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: close Track Details and Segment Analyzer.
- Not allowed: alter imported track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MCT_03 | Closed Track Details, then closed Segment Analyzer. | Stopping the measure tool cleans up temporary markers and listeners. | After closing Segment Analyzer, the side button was no longer active, the analyzer sheet was gone, and the map no longer showed A/B zone markers or analyzer result UI. | PASS | `assets/MCT_03-after-detail-close.webp`, `assets/MCT_03-stopped-clean.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MCT_03-after-detail-close.webp | Segment Analyzer visible again after closing Track Details. |
| assets/MCT_03-stopped-clean.webp | Map after stopping Segment Analyzer. |

## Timings

| Step | Timing |
|---|---:|
| Analyzer cleanup | <3m |

## Handoff Notes

- Completed: MCT_03 terminal PASS.
- Remaining unfinished coverage: continue with MCT_04.
- Blocked or not applicable: none.
- State left for the next packet: map centered on Lannion; Segment Analyzer closed.
