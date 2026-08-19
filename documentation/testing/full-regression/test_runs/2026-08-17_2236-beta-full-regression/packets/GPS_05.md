# Packet: GPS_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_05
- In scope: Disabling active GPS removes its marker and stops live updates when applicable.
- Out of scope: Synthetic marker or update injection.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_04.
- Required app/data state: Active accepted geolocation marker and update stream.
- Required browser context: Geolocation-capable secure origin.

## Allowed Mutations

- Allowed: None on an inapplicable origin.
- Not allowed: Fabricate a marker solely to remove it.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_05 | Evaluated the active-location prerequisite for disable/removal. | Disabling active GPS removes the marker and stops updates. | Remote plain HTTP cannot establish the accepted live marker/update prerequisite, so disable/removal cannot be validly exercised. | NOT APPLICABLE | [assets/GPS_01-http-origin.txt](../assets/GPS_01-http-origin.txt) |

## Issues

- None; prerequisite is inapplicable by the frozen plan.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-http-origin.txt](../assets/GPS_01-http-origin.txt) | Shared secure-origin applicability proof. |

## Screenshot Evidence

- Not applicable on this target.

## Timings

| Step | Timing |
|---|---:|
| Prerequisite applicability check | Under 1 s |

## Handoff Notes

- Completed: GPS_05 applicability recorded independently.
- Remaining unfinished coverage: None for GPS_05.
- Blocked or not applicable: Retest active-marker disable/update stop on localhost or HTTPS.
- State left for the next packet: App unchanged; main map healthy.
