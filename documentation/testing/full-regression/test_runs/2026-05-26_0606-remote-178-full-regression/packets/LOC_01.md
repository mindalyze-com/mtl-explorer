# Packet: LOC_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: LOC_01
- In scope: Numbers, distances, durations, and dates in the selected locale.
- Out of scope: Locale switching, covered by LOC_02.

## Prerequisites

- Required previous coverage IDs or run packets: APP_08
- Required app/data state: Signed in; en-GB format locale selected.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Open Settings and Stats surfaces.
- Not allowed: Change server data for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| LOC_01 | Restored en-GB in Settings, inspected the Settings preview and Stats overview values. | Numbers, distances, durations, and dates render in the expected locale format. | en-GB preview used `26/05/2026` and `12,345.67`; Stats showed grouped values such as `1,262 km`, `7,054 Wh`, dates like `26/05/2026, 11:20`, and compact durations such as `2m 00s`. | PASS | `assets/LOC_01-locale-baseline.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/LOC_01-locale-baseline.txt | Settings and Stats formatting observations for en-GB. |

## Timings

| Step | Timing |
|---|---:|
| Settings and Stats inspection | <2 min |

## Handoff Notes

- Completed: LOC_01 passed.
- Remaining unfinished coverage: LOC_02 through RUN_CLEANUP.
- Blocked or not applicable: None.
- State left for the next packet: Signed in; locale can be changed from Settings.
