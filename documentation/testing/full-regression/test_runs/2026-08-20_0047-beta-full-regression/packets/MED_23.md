# Packet: MED_23

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_23
- In scope: A saved 3,603-second correction, its compact label, and explicit unknown-position provenance when correlation resolution is unavailable.
- Out of scope: Per-card clear reachability after an out-of-window save, already covered by MED_17.

## Prerequisites

- Required previous coverage IDs or run packets: MED_17 and MED_22 cleanup.
- Required app/data state: Eight-item baseline with no manual locations or time corrections.
- Required browser context: Track 100013 Media tab and Statistics Media history mosaic/viewer.

## Allowed Mutations

- Allowed: Save exactly 3,603 seconds through the production endpoint and clear it through the same endpoint.
- Not allowed: Directly edit correction/correlation tables or retain the saved correction.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_23 | Save 3,603 seconds for estimated-a; open it from the all-indexed media mosaic after its route correlation becomes unavailable; expand viewer metadata; clear the correction. | Compact label is valid (`+1h`, not `+1.h`); unavailable provenance says `Position unknown` and never `Photo GPS`. | Fixed locally: the global viewer showed Camera clock · +1h correction, Position unknown, and the clear action on desktop/mobile; clear restored the uncorrected state. | FIXED | [original](../assets/MED_23-seconds-and-unknown-position.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-012-013-fix-local-desktop.webp); [mobile](../assets/MTL-FR-012-013-fix-local-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MTL-FR-013 | P2 | Global media viewer omits saved correction and unknown-position labels. | Save 3,603 s for camera-time-only media, open its adjusted card from Statistics Media, and expand Details. | Viewer shows a valid compact `+1h` correction and `Position unknown`. | Fixed locally: both labels are explicit at desktop and mobile sizes. | [original](../assets/MED_23-seconds-and-unknown-position.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt) | FIXED in the local worktree; remote beta still needs a later build. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_23-seconds-and-unknown-position.txt](../assets/MED_23-seconds-and-unknown-position.txt) | Production API result, read-only database state, accessible UI labels, and cleanup. |

## Screenshot Evidence

![Desktop correction and unknown-position labels](../assets/MTL-FR-012-013-fix-local-desktop.webp)

![Mobile correction and unknown-position labels](../assets/MTL-FR-012-013-fix-local-mobile.webp)

## Fix Record

- `MediaTrendItemDto` now carries the effective time source and applied camera offset through live OpenAPI and the generated client.
- The shared viewer receives an explicit unknown-position state and renders the compact correction label.
- Full client suite 757/757, full server suite 516/516, client generation, and desktop/mobile checks pass. See [local evidence](../assets/MTL-FR-005-021-fix-local.txt).

## Timings

| Step | Timing |
|---|---:|
| Save and recalculate | < 1 s |
| Locate and inspect all-indexed viewer path | 7 min |
| Clear and restore | < 1 s |

## Handoff Notes

- Completed: Exact 3,603-second save, compact-label check, unavailable-provenance check, expanded metadata check, and cleanup.
- Remaining unfinished coverage: None for MED_23.
- Blocked or not applicable: Screenshot evidence remains blocked by ACC_04.
- State left for the next packet: Exact eight-item baseline; no manual/correction/work rows; media 400002 selected and TRACK_INTERPOLATED again.
