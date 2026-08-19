# Packet: GPS_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_01
- In scope: Determine secure-origin applicability for live geolocation.
- Out of scope: Simulating a location or permission state.

## Prerequisites

- Required previous coverage IDs or run packets: HMO_03.
- Required app/data state: Healthy signed-in quick install.
- Required browser context: Remote target URL.

## Allowed Mutations

- Allowed: Read-only URL/applicability inspection.
- Not allowed: Browser-policy bypass or fabricated geolocation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_01 | Compared the target origin with the frozen secure-origin rule. | Remote plain HTTP is recorded as expected browser limitation; test live GPS on localhost/HTTPS. | Origin is `http://62.238.106.141:18080`, neither localhost nor HTTPS. | NOT APPLICABLE | [assets/GPS_01-http-origin.txt](../assets/GPS_01-http-origin.txt) |

## Issues

- None; this is an explicit frozen-plan applicability outcome.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/GPS_01-http-origin.txt](../assets/GPS_01-http-origin.txt) | Target scheme/host and frozen secure-origin rule. |

## Screenshot Evidence

- Not applicable; URL and frozen-plan rule determine this row.

## Timings

| Step | Timing |
|---|---:|
| Applicability check | Under 1 s |

## Handoff Notes

- Completed: Secure-origin applicability decision.
- Remaining unfinished coverage: None for GPS_01.
- Blocked or not applicable: Live GPS checks require localhost or HTTPS.
- State left for the next packet: App unchanged.
