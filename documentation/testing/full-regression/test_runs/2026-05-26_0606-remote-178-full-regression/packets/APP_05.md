# Packet: APP_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: APP_05
- In scope: Hard refresh in dark mode.
- Out of scope: Frame-by-frame video capture of initial paint.

## Prerequisites

- Required previous coverage IDs or run packets: APP_04
- Required app/data state: Dark mode selected before hard reload.
- Required browser context: Desktop browser.

## Allowed Mutations

- Allowed: Hard reload the page.
- Not allowed: Clear local preferences.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| APP_05 | Hard reloaded while dark mode was selected and inspected the first captured post-load theme state plus startup theme code. | Hard refresh in dark mode does not flash light theme first. | The first captured post-DOM-load state already had `data-theme=dark`; the app applies the stored scheme at `useTheme.ts` module import before Vue mounts. No light mounted state was observed. | PASS | `assets/APP_04_05-theme-persistence.txt`, `mtl-client/src/composables/useTheme.ts` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/APP_04_05-theme-persistence.txt | Hard-reload dark-mode state evidence. |

## Timings

| Step | Timing |
|---|---:|
| Hard reload dark-mode check | <1 min |

## Handoff Notes

- Completed: APP_05 passed.
- Remaining unfinished coverage: APP_06 through RUN_CLEANUP.
- Blocked or not applicable: Frame-by-frame first-paint capture was not available; DOM post-load state and startup code were verified.
- State left for the next packet: Signed in, dark mode selected.
