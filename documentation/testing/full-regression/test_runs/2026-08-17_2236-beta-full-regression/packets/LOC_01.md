# Packet: LOC_01

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_01
- In scope: Default-locale number, distance, duration, and date formatting.
- Out of scope: Locale switching and measurement-unit conversion.

## Prerequisites

- Required previous coverage IDs or run packets: APP_08.
- Required app/data state: Authenticated 15-track map and Statistics data.
- Required browser context: Browser-detected `en-GB`, Europe/Zurich.

## Allowed Mutations

- Allowed: Read Preferences and Statistics views.
- Not allowed: Change locale or units.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_01 | Read the detected locale/preview, measurement preview, Statistics totals, and recent activity formats. | Numbers, distances, durations, and dates use the expected locale format. | `en-GB` rendered `12,345.67`, `18/08/2026`, `824 km`, `16h 51m`, `3,993 Wh`, `247.23 m`, and `0m 45s` coherently. | PASS | [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt) | Exact locale preview and representative values across Preferences and Statistics. |

## Screenshot Evidence

- The later LOC_03 screenshot shows the same Region & units surface after locale persistence; exact LOC_01 values are preserved in the text evidence.

## Timings

| Step | Timing |
|---|---:|
| Preferences and Statistics inspection | Under 2 seconds each |

## Handoff Notes

- Completed: Expected `en-GB` formatting across representative surfaces.
- Remaining unfinished coverage: None for LOC_01.
- Blocked or not applicable: None.
- State left for the next packet: Preferences available for a locale switch.
