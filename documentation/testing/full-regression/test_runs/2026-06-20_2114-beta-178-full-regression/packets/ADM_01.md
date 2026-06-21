# Packet: ADM_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_01
- In scope: Open Admin and verify the panel list is reachable and usable.
- Out of scope: Individual Admin panel workflows.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_04 terminal.
- Required app/data state: Authenticated map view.
- Required browser context: Desktop Chromium against the remote target.

## Allowed Mutations

- Allowed: Open Admin and inspect available tiles.
- Not allowed: Change server data for this packet.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_01 | Opened Admin from the app route/navigation and inspected the Admin workspace. | Admin opens and the panel list is reachable and usable. | PASS. Admin opened with reachable tiles for Upload, Jobs, Freshness, Garmin Sync, Log, Helpers, About, Settings, Session, and Attribution; all expected tiles exposed `Open <label>` actions. | PASS | [assets/ADM_01-admin-home.webp](../assets/ADM_01-admin-home.webp); [assets/ADM-admin-results.txt](../assets/ADM-admin-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
|  |  |  |  |  |  |  |  |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_01-admin-home.webp](../assets/ADM_01-admin-home.webp) | Admin workspace tile list. |
| [assets/ADM-admin-results.txt](../assets/ADM-admin-results.txt) | Compact Admin validation summary for ADM_01. |

## Screenshot Evidence

![Admin home](../assets/ADM_01-admin-home.webp)

## Timings

| Step | Timing |
|---|---:|
| Open Admin and capture home | <1 min |

## Handoff Notes

- Completed: ADM_01 is terminal PASS.
- Remaining unfinished coverage: ADM_02 onward.
- Blocked or not applicable: none.
- State left for the next packet: Admin remained available for panel workflow checks.
