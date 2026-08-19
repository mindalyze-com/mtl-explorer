# Packet: MED_35

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_35
- In scope: Clean Photos timeline, single Photo tools disclosure, preview, Adjust locations/edit actions, editor-close behavior, desktop/390 x 760, and scale paging.
- Out of scope: Preview correctness beyond usability, otherwise covered deeply by MED_16.

## Prerequisites

- Required previous coverage IDs or run packets: MED_34, MED_16, and MED_21.
- Required app/data state: Six-photo activity plus 100,000-photo paging fixture.
- Required browser context: Desktop and 390 x 760 activity Photos.

## Allowed Mutations

- Allowed: Unsaved offset preview and unsaved location editor; both must be reset/closed.
- Not allowed: Saving corrections or assignments.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_35 | Repeated Photo tools preview/reset on the corrected 4/2 fixture at desktop and 390 x 760. | Preview remains usable and Save is enabled when camera-clock rows can be adjusted. | Preview retained six rows, shifted the two camera-clock items, preserved the four EXIF GPS items, and enabled Save at both sizes. The earlier zero result came from invalid fixture timing. | REJECTED | [retest](../assets/MED_35-retest.txt); [desktop](../assets/MED_35-rejected-desktop.webp); [mobile](../assets/MED_35-rejected-mobile.webp) |

## Issues

| Issue | Severity | Summary | Reproduction | Expected | Actual | Evidence | Impact |
|---|---|---|---|---|---|---|---|
| FR-010 | P1 | Valid camera preview empties the activity Photos tab. | Open activity 100016 Photos, Photo tools, enter +0.25 h, and Preview on desktop or 390 x 760. | Preview remains usable and shows affected photo locations. | Activity photos changes 6 to 0; Reset is required to recover. | [assets/MED_35-photo-tools.txt](../assets/MED_35-photo-tools.txt) | Blocks the intended camera-clock workflow exposed by Photo tools. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_35-photo-tools.txt](../assets/MED_35-photo-tools.txt) | Per-viewport disclosure/actions, FR-010 result, paging blocker, and cleanup. |

## Screenshot Evidence

- Live screenshots showed the clean desktop/mobile timelines and compact phone disclosure before/after editor cleanup. Exact control counts are linked above.

## Timings

| Step | Timing |
|---|---:|
| Disclosure open/close | Under 200 ms |
| Preview/reset | Under 400 ms each |
| Editor open/close | Under 150 ms each |

## Handoff Notes

- Completed: Full desktop/phone disclosure and cleanup flow.
- Remaining unfinished coverage: None for MED_35; missing scale pages are terminally blocked within the failing packet.
- Blocked or not applicable: 100,000-photo first/next/last paging.
- State left for the next packet: Preview reset, no editor/mutation, desktop 1280 x 720, Photo tools closed.

## Remediation Verification

- Finding FR-010 is `REJECTED`: Photo tools are usable with the corrected mixed-time-source fixture.
- The unrelated absent 100,000-photo scale fixture remains outside this finding's remediation scope.
