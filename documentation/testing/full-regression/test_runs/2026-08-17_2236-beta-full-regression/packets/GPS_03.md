# Packet: GPS_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: GPS_03
- In scope: Follow-me centering and drifted state after manual pan when live GPS is available.
- Out of scope: Synthetic position injection.

## Prerequisites

- Required previous coverage IDs or run packets: GPS_02.
- Required app/data state: Accepted live location with active updates.
- Required browser context: Geolocation-capable secure origin.

## Allowed Mutations

- Allowed: None on an inapplicable origin.
- Not allowed: Invent position updates to satisfy follow mode.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| GPS_03 | Evaluated the live-location prerequisite for Follow me and drift. | With accepted live updates, map follows until user pan creates drifted state. | GPS_02 is not applicable on remote plain HTTP, so no standards-valid marker/update stream exists for this row. | NOT APPLICABLE | [assets/GPS_01-http-origin.txt](../assets/GPS_01-http-origin.txt) |

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

- Completed: GPS_03 applicability recorded independently.
- Remaining unfinished coverage: None for GPS_03.
- Blocked or not applicable: Retest Follow me/drift on localhost or HTTPS with live updates.
- State left for the next packet: App unchanged.
