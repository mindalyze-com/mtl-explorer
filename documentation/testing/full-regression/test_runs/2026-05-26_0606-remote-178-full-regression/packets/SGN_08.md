# Packet: SGN_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_08
- In scope: public-facing About branding.
- Out of scope: full legal/license validation.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_02.
- Required app/data state: app running and signed in.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: open About dialog.
- Not allowed: change settings or server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_08 | Opened About dialog after sign-in. | `MTL Explorer` branding appears in About/public-facing copy. | About dialog heading and copy both used `MTL Explorer`. | PASS | `assets/SGN_08-about-branding.webp`, `assets/SGN_08-about-branding.txt` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/SGN_08-about-branding.webp | About dialog branding. |
| assets/SGN_08-about-branding.txt | Text excerpt confirming branding. |

## Timings

| Step | Timing |
|---|---:|
| About branding check | <1m |

## Handoff Notes

- Completed: SGN_08 terminal PASS.
- Remaining unfinished coverage: continue with SGN_09.
- Blocked or not applicable: none.
- State left for the next packet: no server data changed.
