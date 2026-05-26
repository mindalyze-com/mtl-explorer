# Packet: MAP_07

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: MAP_07
- In scope: direction-arrow applicability.
- Out of scope: ordinary track line geometry, covered by MAP_05.

## Prerequisites

- Required previous coverage IDs or run packets: MAP_05.
- Required app/data state: app running with track lines visible.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: inspect settings/config.
- Not allowed: change settings.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MAP_07 | Checked config and Settings UI for a direction-arrow setting. | Direction arrows appear at high zoom if enabled in settings. | No direction-arrow setting was exposed in config or Settings; the conditional check is not applicable in this run. | NOT APPLICABLE | `assets/MAP_07-direction-arrow-config.txt`, `assets/MAP_07-settings-scan.txt`, `assets/MAP_07-settings-scan.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/MAP_07-direction-arrow-config.txt | Config probe showing no direction-arrow setting. |
| assets/MAP_07-settings-scan.txt | Settings text scan. |
| assets/MAP_07-settings-scan.webp | Settings UI screenshot. |

## Timings

| Step | Timing |
|---|---:|
| Settings/config scan | <1m |

## Handoff Notes

- Completed: MAP_07 terminal NOT APPLICABLE.
- Remaining unfinished coverage: continue with MAP_08.
- Blocked or not applicable: direction arrows are conditional and no enabled/exposed setting exists in this run.
- State left for the next packet: no data changed.
