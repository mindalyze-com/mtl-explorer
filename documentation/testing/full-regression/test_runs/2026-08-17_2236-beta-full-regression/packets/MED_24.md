# Packet: MED_24

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_24
- In scope: Immediate same-bounds marker refresh and no-store response after manual set/clear.
- Out of scope: Main-map viewport request scheduling, covered by MED_02.

## Prerequisites

- Required previous coverage IDs or run packets: MED_22.
- Required app/data state: Estimated item 400000 with no manual assignment.
- Required browser context: Activity Photos Adjust locations.

## Allowed Mutations

- Allowed: Save and clear one disposable manual assignment; authenticated read of the same map bounds.
- Not allowed: Delayed or changed-bounds substitution.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_24 | Read baseline bounds, saved manual 46.9484,7.4479 and immediately reread, cleared and immediately reread the exact URL. | Marker changes immediately and response is no-store. | Coordinates changed baseline→manual→baseline on consecutive same-bounds reads; each was HTTP 200 with Cache-Control: no-store. | PASS | [assets/MED_24-no-store-refresh.txt](../assets/MED_24-no-store-refresh.txt) |

## Issues

None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_24-no-store-refresh.txt](../assets/MED_24-no-store-refresh.txt) | Exact bounds, headers, and coordinate transitions. |

## Screenshot Evidence

Accessible Set by you/Estimated UI labels plus exact HTTP payload/headers provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Each immediate bounds read | About 0.3 s |

## Handoff Notes

- Completed: Immediate no-store refresh passed.
- Remaining unfinished coverage: None for MED_24.
- Blocked or not applicable: None.
- State left for the next packet: Manual assignment cleared; baseline restored.
