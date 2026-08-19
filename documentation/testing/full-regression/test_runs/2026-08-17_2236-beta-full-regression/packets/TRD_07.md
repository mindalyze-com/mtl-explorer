# Packet: TRD_07

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_07
- In scope: Track-shape previews in the track browser, filters, statistics, related tracks, and overlapping-track selection lists.
- Out of scope: Detail mini-map behavior and preview screenshot styling.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01, TRD_02, and DAT_07.
- Required app/data state: Fifteen visible tracks, including the controlled Bern overlap pair.
- Required browser context: Authenticated desktop browser.

## Allowed Mutations

- Allowed: Navigate among read-only track lists and select the controlled overlap location.
- Not allowed: Edit track records.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_07 | Inspected rendered shape previews in Related, Filter Review Tracks, Statistics View all tracks, and the controlled Bern overlapping-track selection list. | Each surface presents a visible track-shape preview for loaded rows. | All four surfaces rendered non-empty SVG track paths at stable non-zero dimensions. Off-viewport list rows loaded lazily as expected. | PASS | [assets/TRD_07-shape-previews.txt](../assets/TRD_07-shape-previews.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_07-shape-previews.txt](../assets/TRD_07-shape-previews.txt) | DOM dimensions, SVG paths, row counts, and lazy-loading observations for all required list surfaces. |

## Screenshot Evidence

Unavailable under ACC_04. Rendered DOM dimensions and non-empty SVG path data provide direct visual-component evidence.

## Timings

| Step | Timing |
|---|---:|
| Related and filter previews | About 2 min |
| Statistics and selection-list previews | About 2 min |

## Handoff Notes

- Completed: Track-shape previews on all required surfaces.
- Remaining unfinished coverage: None for TRD_07.
- Blocked or not applicable: Screenshot evidence only; covered by ACC_04 and does not block the rendered preview assertion.
- State left for the next packet: Bern overlapping-track selection list open at 100 m.
