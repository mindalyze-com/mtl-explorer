# Packet: LOC_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: LOC_02
- In scope: Changing locale and checking live formatting updates across app surfaces.
- Out of scope: Persistence across reload, covered by LOC_03.

## Prerequisites

- Required previous coverage IDs or run packets: LOC_01
- Required app/data state: Signed in; Settings available.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Change local format locale preference.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_02 | Changed format locale from en-GB to de-DE, then inspected Settings preview and Stats overview/recent activity. | Changing locale updates formatting across the app without reload artifacts. | Settings preview, dates, and grouped values updated immediately to de-DE style, but decimal unit values such as `94.26 m`, `3.60 km`, and `72.5 km/h` kept period decimal separators instead of de-DE comma separators. | FAIL | `assets/LOC_02-locale-switch.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| LOC-01 | P2 | Locale switch does not update decimal-unit formatting on some Stats values. | Open Admin -> Settings, select de-DE, then inspect Stats Overview and Recent Activity values. | Decimal unit values should use de-DE comma separators after selecting de-DE. | Dates and grouped values update, but decimal distances/speeds still show period separators such as `94.26 m`, `3.60 km`, and `72.5 km/h`. | `assets/LOC_02-locale-switch.txt` | Users selecting a locale with comma decimals see mixed numeric formats across the same Stats surface. |

## Evidence Files

| File | Purpose |
|---|---|
| assets/LOC_02-locale-switch.txt | Locale switch observations and formatting mismatch. |

## Timings

| Step | Timing |
|---|---:|
| Locale switch and Stats inspection | <3 min |

## Handoff Notes

- Completed: LOC_02 terminal `FAIL`.
- Remaining unfinished coverage: LOC_03 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: de-DE locale selected for persistence check.
