# Packet: MED_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_03
- In scope: Open media from a pin and navigate photo next/previous.
- Out of scope: HEIC and broken-photo handling.

## Prerequisites

- Required previous coverage IDs or run packets: MED_02.
- Required app/data state: Eight-item Bern cluster chooser open.
- Required browser context: Main-map media viewer.

## Allowed Mutations

- Allowed: Open cluster collection, select a photo, and navigate.
- Not allowed: Edit photo metadata.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_03 | Open This cluster, select a JPG, use Next and Previous. | Photo preview opens and both navigation directions work. | Viewer opened eight items; mtl-regression-delete-a.jpg rendered with details/location, Next opened mtl-regression-photo-b.jpg, and Previous returned. | PASS | [assets/MED_03-photo-navigation.txt](../assets/MED_03-photo-navigation.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_03-photo-navigation.txt](../assets/MED_03-photo-navigation.txt) | Viewer selection and bidirectional navigation states. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible image filenames, counters, and controls are linked above.

## Timings

| Step | Timing |
|---|---:|
| Open cluster/photo | 1 min |
| Next/previous | <1 min |

## Handoff Notes

- Completed: Pin-to-photo preview and navigation.
- Remaining unfinished coverage: None for MED_03.
- Blocked or not applicable: None.
- State left for the next packet: Viewer open on mtl-regression-delete-a.jpg, item 2 of 8.
