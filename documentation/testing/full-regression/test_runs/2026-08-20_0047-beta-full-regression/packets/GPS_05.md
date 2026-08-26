# Packet: GPS_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_05
- In scope: Disable an active GPS session, remove its marker, and stop updates.
- Out of scope: Static disabled-state messaging covered by GPS_04.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01-04.
- Required app/data state: An accepted live GPS session/marker would be required.
- Required browser context: Configured remote plain-HTTP origin.

## Allowed Mutations

- Allowed: None beyond the attempted GPS activation.
- Not allowed: Mock a marker/update stream or change origin.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_05 | Checked whether the visible GPS activation could create an active session that could then be disabled. | On localhost/HTTPS after accepted permission, disabling removes the marker and stops updates. | Secure-origin rejection prevents an active session, marker, or update stream from existing. There is therefore no live disable transition to execute on this configured origin; frozen GPS_01 classifies it as expected browser limitation. | NOT APPLICABLE | [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) |

## Issues

No product issue; expected browser security behavior.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-http-limitation.txt](../assets/GPS_01-http-limitation.txt) | Exact origin and absence of any live GPS session/marker. |

## Screenshot Evidence

Not applicable; no active GPS marker can exist on this origin.

## Timings

| Step | Timing |
|---|---:|
| Applicability check | Immediate from GPS_01-04 evidence |

## Handoff Notes

- Completed: Applicability was directly resolved against the configured origin.
- Remaining unfinished coverage: None for GPS_05.
- Blocked or not applicable: NOT APPLICABLE - expected browser limitation.
- State left for the next packet: GPS disabled and no marker; filter/layers remain at baseline.
