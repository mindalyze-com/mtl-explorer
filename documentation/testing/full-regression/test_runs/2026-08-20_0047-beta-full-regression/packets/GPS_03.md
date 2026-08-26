# Packet: GPS_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_03
- In scope: Live Follow me centering and drifted state after user pan.
- Out of scope: General map pan already covered elsewhere.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01-02.
- Required app/data state: A live accepted GPS position would be required.
- Required browser context: Configured remote plain-HTTP origin.

## Allowed Mutations

- Allowed: None beyond the already attempted visible GPS activation.
- Not allowed: Mock GPS, inject coordinates, or change origin.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Checked whether GPS could enter an accepted live state from which Follow me and drifted pan could be exercised. | On localhost/HTTPS with accepted permission, Follow me centers until the user pans. | GPS is rejected on the configured remote HTTP origin before any live position, marker, or Follow me control exists. Frozen GPS_01 requires this live row to be classified as expected browser limitation. | NOT APPLICABLE | [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) |

## Issues

No product issue; expected browser security behavior.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) | Exact origin and live-GPS guard evidence. |

## Screenshot Evidence

Not applicable; Follow me cannot exist without a permitted live position.

## Timings

| Step | Timing |
|---|---:|
| Applicability check | Immediate from GPS_01/02 evidence |

## Handoff Notes

- Completed: Applicability was directly resolved against the configured origin.
- Remaining unfinished coverage: None for GPS_03.
- Blocked or not applicable: NOT APPLICABLE - expected browser limitation.
- State left for the next packet: GPS disabled; map unchanged.
