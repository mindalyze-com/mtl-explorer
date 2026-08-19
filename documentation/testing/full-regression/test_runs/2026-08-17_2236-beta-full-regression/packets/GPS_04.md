# Packet: GPS_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_04
- In scope: Clear permission-denied/disabled message when live geolocation is applicable.
- Out of scope: Fabricated permission states.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_03.
- Required app/data state: GPS tool on a secure origin where the browser exposes a permission decision.
- Required browser context: Localhost or HTTPS.

## Allowed Mutations

- Allowed: None on an inapplicable origin.
- Not allowed: Claim browser policy rejection as an end-user denied permission choice.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_04 | Applied the secure-origin rule to the explicit denied/disabled UI row. | A user denial or disabled location on a secure origin produces a clear message. | Remote plain HTTP does not expose the required standards-valid prompt/decision path; no denied state was fabricated. | NOT APPLICABLE | [assets/GPS_01-http-origin.txt](../assets/GPS_01-http-origin.txt) |

## Issues

- None; origin applicability prevents a valid user-denial test.

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

- Completed: GPS_04 applicability recorded independently.
- Remaining unfinished coverage: None for GPS_04.
- Blocked or not applicable: Retest denial/disabled messaging on localhost or HTTPS.
- State left for the next packet: App unchanged.
