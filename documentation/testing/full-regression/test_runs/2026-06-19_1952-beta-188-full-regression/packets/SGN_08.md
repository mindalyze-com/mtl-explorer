# Packet: SGN_08

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_08
- In scope: Verify that `MTL Explorer` branding appears in About or public-facing copy.
- Out of scope: Detailed build metadata correctness.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_07.
- Required app/data state: Quick-install stack recovered and authenticated app shell usable.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Open the Admin/About surface.
- Not allowed: Change data, preferences, or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_08 | Opened the authenticated app shell, selected Admin, then opened the About surface. | Public-facing copy or About area uses the required `MTL Explorer` branding. | The page title was `MTL Explorer`; the map shell exposed a public `About MTL Explorer` button; Admin > About opened build/runtime copy. | PASS | [assets/SGN_08-about-branding.txt](../assets/SGN_08-about-branding.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SGN_08-about-branding.txt](../assets/SGN_08-about-branding.txt) | DOM evidence for MTL Explorer branding and About copy. |

## Screenshot Evidence

No screenshot asset was captured for this packet; browser screenshot capture timed out after the SGN_07 outage/recovery test, so direct DOM evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| About branding check | ~3 min |

## Handoff Notes

- Completed: SGN_08.
- Remaining unfinished coverage: SGN_09 onward.
- Blocked or not applicable: none.
- State left for the next packet: Browser automation kernel reset during screenshot retry; reconnect before SGN_09.
