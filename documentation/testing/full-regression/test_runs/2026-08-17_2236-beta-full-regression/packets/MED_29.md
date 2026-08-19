# Packet: MED_29

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_29
- In scope: Shape, color, and accessible provenance for Photo GPS, Estimated, Set by you, and unknown positions in activity/viewer maps; manual-clear restoration.
- Out of scope: Main-map clustering.

## Prerequisites

- Required previous coverage IDs or run packets: MED_28.
- Required app/data state: Photo GPS, Estimated, manual-assignable, and unknown-position media.
- Required browser context: Track Details Photos mini-map and viewer Details.

## Allowed Mutations

- Allowed: Temporary manual location through Photo tools, then clear it.
- Not allowed: Keeping manual fixture mutations.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_29 | Compared Photo GPS and Estimated markers, set media 400000 manually, inspected both maps/accessibility, then cleared it. | Every provenance uses one circular camera shape; only color/text differ; clearing restores prior color/label. | Photo GPS blue, Estimated brown, and Set by you purple all used the same circular camera glyph with correct accessible text in both tested maps. Clear restored Estimated. No unknown-position fixture exists. | BLOCKED | [assets/MED_29-provenance-markers.txt](../assets/MED_29-provenance-markers.txt) |

## Issues

- Missing unknown-position fixture; the three available provenance states passed.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_29-provenance-markers.txt](../assets/MED_29-provenance-markers.txt) | Exact visual/accessibility states and mutation cleanup. |

## Screenshot Evidence

- Live screenshot inspection compared the blue, brown, and purple circular camera markers in both maps. The browser image result was not exposed as a durable local file; exact semantic labels are linked above.

## Timings

| Step | Timing |
|---|---:|
| Manual save and mini-map update | Under 1 s |
| Clear and provenance restoration | Under 1 s |

## Handoff Notes

- Completed: Photo GPS, Estimated, Set by you, and clear-restoration branches.
- Remaining unfinished coverage: None for MED_29; the missing unknown branch is terminally blocked.
- Blocked or not applicable: Unknown-position visual/accessibility state.
- State left for the next packet: Manual assignment cleared; media 400000 is Estimated again.
