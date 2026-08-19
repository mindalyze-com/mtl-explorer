# Packet: TBS_13

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_13
- In scope: Pointer and keyboard activation of the filtered Statistics Overview summary on desktop and mobile.
- Out of scope: Unfiltered summary behavior and unrelated Statistics controls.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_12.
- Required app/data state: One-track rectangle active against the stable 13-track set.
- Required browser context: Statistics Overview at desktop and 390 x 844 mobile viewports.

## Allowed Mutations

- Allowed: Open/close Statistics and Filter; create and close a mobile tab; reset the temporary geo filter after the checks.
- Not allowed: Change tracks or curation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_13 | Focused the filtered summary and activated it by Enter and Space on the fixed build at desktop and mobile sizes. | Keyboard activation opens Filter directly once in both layouts. | Enter and Space opened Filter at both viewports without duplicate activation. | FIXED | [details](../assets/TBS_13-remediation.txt); [desktop](../assets/TBS_13-fixed-desktop.webp); [mobile](../assets/TBS_13-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-007 | P2 | The filtered Statistics summary is not keyboard-activatable. | Apply a filter, open Statistics Overview, focus "Showing X of Y tracks", and press Enter or Space on desktop or mobile. | Filter opens directly, matching pointer activation. | Focus remains on the summary and Statistics stays open; Filter does not open. | [assets/TBS_13-summary-activation.txt](../assets/TBS_13-summary-activation.txt) | Keyboard-only users cannot use the summary shortcut. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_13-summary-activation.txt](../assets/TBS_13-summary-activation.txt) | Desktop/mobile pointer and keyboard outcomes plus cleanup. |

## Screenshot Evidence

Unavailable under ACC_04. Accessible names, focused state, open-panel headings, and viewport-specific DOM provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Desktop pointer and keyboard | About 8 s |
| Mobile pointer and keyboard | About 9 s |
| Restore viewport/filter baseline | About 2 s |

## Handoff Notes

- Completed: Desktop/mobile pointer and keyboard activation checks.
- Remaining unfinished coverage: None for TBS_13.
- Blocked or not applicable: Screenshot capture remains blocked under ACC_04.
- State left for the next packet: Desktop default viewport; Filter open; no active criteria; 13 matching tracks.

## Remediation Verification

- Finding FR-007 is `FIXED`: explicit keyboard handling now opens Filter from the summary.
- Pointer behavior remains unchanged and automated tests cover one activation per key.
