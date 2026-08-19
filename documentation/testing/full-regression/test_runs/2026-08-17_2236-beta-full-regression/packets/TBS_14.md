# Packet: TBS_14

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_14
- In scope: Media chart ordering, default all-indexed behavior, and visible/hover/focus help for all-indexed and track-related modes.
- Out of scope: Timeline slot values and period drill-downs.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_13.
- Required app/data state: Stable unfiltered 13-track set and six indexed media items.
- Required browser context: Statistics Trends Charts.

## Allowed Mutations

- Allowed: Switch Media timeline modes and Trends Table/Charts views.
- Not allowed: Change tracks, media, filters, or curation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TBS_14 | Keyboard-focused Media history and Matched only on the fixed build at desktop and mobile sizes. | Focus exposes the same concise explanation as hover. | A visible `role=tooltip` description appeared for both controls and was linked by `aria-describedby`; blur removed it. | FIXED | [details](../assets/TBS_14-remediation.txt); [desktop](../assets/TBS_14-fixed-desktop.webp); [mobile](../assets/TBS_14-fixed-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-008 | P2 | Media timeline explanations are unavailable on keyboard focus. | Open Statistics > Trends > Charts, focus an all-indexed mode or Matched only with the keyboard, and wait for its help tooltip. | Focus exposes the same explanation available on hover. | Visible help remains, but no focus tooltip is created; hover creates the expected tooltip. | [assets/TBS_14-media-help.txt](../assets/TBS_14-media-help.txt) | Keyboard-only users cannot access the contextual mode explanation promised by the control. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_14-media-help.txt](../assets/TBS_14-media-help.txt) | Chart order, selected modes, exact help text, hover/focus behavior, and keyboard-path validation. |

## Screenshot Evidence

Unavailable under ACC_04. Exact pressed state, help text, tooltip DOM, and focused element provide direct evidence.

## Timings

| Step | Timing |
|---|---:|
| Chart/default inspection | About 3 s |
| Hover/focus help checks | About 8 s |

## Handoff Notes

- Completed: Media chart order, default, and help behavior.
- Remaining unfinished coverage: None for TBS_14.
- Blocked or not applicable: Screenshot capture remains blocked under ACC_04.
- State left for the next packet: Trends Charts open; Activity era selected; quarter grouping; all sub-units; 13 tracks and six indexed media items.

## Remediation Verification

- Finding FR-008 is `FIXED`: Statistics media help is available by keyboard focus as well as hover.
- Automated coverage verifies tooltip copy, ARIA linkage, and blur cleanup.
