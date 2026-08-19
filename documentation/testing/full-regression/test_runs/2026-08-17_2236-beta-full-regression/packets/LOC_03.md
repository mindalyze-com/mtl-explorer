# Packet: LOC_03

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: LOC_03
- In scope: Locale persistence across reload.
- Out of scope: Measurement-unit persistence.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_02.
- Required app/data state: `de-DE` selected through the UI.
- Required browser context: Statistics open in the authenticated desktop tab.

## Allowed Mutations

- Allowed: One full page reload and read-only checks.
- Not allowed: Reselect locale after reload.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_03 | Reloaded Statistics, verified de-DE values, then reopened Preferences. | Locale persists across reload. | Statistics retained `3.993 Wh`, `72,5 km/h`, and `18.08.2026`; Preferences still selected `de-DE` and previewed `12.345,67`. | PASS | [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt); [assets/LOC_03-de-de-persisted.jpg](../assets/LOC_03-de-de-persisted.jpg) |

## Issues

- None.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/LOC-locale-results.txt](../assets/LOC-locale-results.txt) | Exact post-reload values and selected-locale assertion. |
| [assets/LOC_03-de-de-persisted.jpg](../assets/LOC_03-de-de-persisted.jpg) | Persisted de-DE selection and preview after reload. |

## Screenshot Evidence

- The Preferences screenshot shows `de-DE (31.12.2025, 1.234,56)` and a `12.345,67` preview after the full reload.

## Timings

| Step | Timing |
|---|---:|
| Reload settle | 1.8 seconds |

## Handoff Notes

- Completed: de-DE persisted across reload and across Statistics/Preferences.
- Remaining unfinished coverage: None for LOC_03.
- Blocked or not applicable: None.
- State left for the next packet: Persisted de-DE active.
