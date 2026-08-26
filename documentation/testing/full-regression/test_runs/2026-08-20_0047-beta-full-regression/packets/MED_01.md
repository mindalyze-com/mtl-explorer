# Packet: MED_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_01
- In scope: Default media layer, photo pins, and disabled/enabled persistence across reload.
- Out of scope: Viewport fetching and viewer navigation.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_04 and MED_06.
- Required app/data state: Eight indexed synthetic media items near Bern.
- Required browser context: Main map and Map > Your data.

## Allowed Mutations

- Allowed: Reset map settings, toggle media layer, reload, and click a synthetic cluster.
- Not allowed: Change media files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_01 | Reset map settings, verify default media, toggle off/reload, toggle on/reload, and open cluster chooser. | Media defaults on with pins and each explicit state persists. | Default toggle was on and an eight-photo cluster opened; off survived reload as 2/4 layers, and on survived reload as 3/4 layers. | PASS | [assets/MED_01-layer-persistence.txt](../assets/MED_01-layer-persistence.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_01-layer-persistence.txt](../assets/MED_01-layer-persistence.txt) | Default, persisted toggles, and clickable pin evidence. |

## Screenshot Evidence

Screenshot capture is BLOCKED in ACC_04; accessible layer/cluster state is linked above.

## Timings

| Step | Timing |
|---|---:|
| Reset/default audit | 1 min |
| Off/on reload persistence | 2 min |
| Click cluster | 1 min |

## Handoff Notes

- Completed: Default and persisted media layer states plus live pin interaction.
- Remaining unfinished coverage: None for MED_01.
- Blocked or not applicable: None.
- State left for the next packet: Media layer enabled; Bern map at 100 m scale.
