# Packet: MED_22

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_22
- In scope: Manual-position precedence while previewing a non-zero camera correction.
- Out of scope: Saved correction persistence.

## Prerequisites

- Required previous coverage IDs or run packets: MED_18 and MED_21.
- Required app/data state: Estimated item 400000 and a valid manual assignment.
- Required browser context: Activity Photos, Photo tools, and Adjust locations.

## Allowed Mutations

- Allowed: Save/clear one disposable manual location and apply/reset an unsaved preview.
- Not allowed: Save a camera correction.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_22 | Assigned a manual location in the corrected fixture, previewed +0.25 h, then reset and cleared it on desktop and mobile. | A still-matched row retains its Set by you position and clear action during preview. | The manually positioned row remained visible and USER_ASSIGNED during the valid preview. The earlier five-minute fixture shifted every camera-clock row outside the activity, where manual position is not an activity-membership override. | REJECTED | [retest](../assets/MED_22-retest.txt); [desktop](../assets/MED_22-rejected-desktop.webp); [mobile](../assets/MED_22-rejected-mobile.webp) |

## Issues

- FR-010 from MED_16 also breaks manual-location precedence in the preview UI.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_22-manual-preview.txt](../assets/MED_22-manual-preview.txt) | Manual before/preview/reset/clear states and persistence control. |

## Screenshot Evidence

Live viewport capture showed the zero-photo preview; accessible states and exact persistence values are recorded durably.

## Timings

| Step | Timing |
|---|---:|
| Manual save | About 1.1 s |
| Offset preview | About 1.3 s |
| Reset and cleanup | About 2 s |

## Handoff Notes

- Completed: Manual-position preview boundary tested; FR-010 impact recorded.
- Remaining unfinished coverage: None for MED_22.
- Blocked or not applicable: None.
- State left for the next packet: Manual location cleared; six-photo baseline restored.

## Remediation Verification

- Finding FR-010 is `REJECTED`: valid input preserves manual position provenance for retained preview rows.
- No product matching or membership rule was changed.
