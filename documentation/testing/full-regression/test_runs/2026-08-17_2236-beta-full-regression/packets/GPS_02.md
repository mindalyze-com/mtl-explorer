# Packet: GPS_02

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_02
- In scope: Enable-GPS permission and accepted locate-marker behavior when applicable.
- Out of scope: Fabricated browser permission or coordinates.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_01.
- Required app/data state: Healthy quick install.
- Required browser context: Geolocation-capable secure origin.

## Allowed Mutations

- Allowed: None on an inapplicable origin.
- Not allowed: Override secure-context policy or inject a test location.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_02 | Applied the GPS_01 secure-origin decision to the permission/accepted-marker row. | On localhost/HTTPS, enabling GPS prompts and acceptance places a marker. | Remote plain HTTP cannot provide the required standards-compliant live permission/accepted-location path; no location was fabricated. | NOT APPLICABLE | [assets/GPS_01-http-origin.txt](../assets/GPS_01-http-origin.txt) |

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

- Completed: GPS_02 applicability recorded independently.
- Remaining unfinished coverage: None for GPS_02.
- Blocked or not applicable: Retest permission/marker behavior on localhost or HTTPS.
- State left for the next packet: App unchanged.
