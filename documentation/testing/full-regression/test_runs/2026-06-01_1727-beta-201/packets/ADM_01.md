# Packet: ADM_01

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: ADM_01
- In scope: Open the Admin dialog and verify the available admin entries are reachable.
- Out of scope: Functional checks inside each Admin panel; covered by ADM_02 through ADM_11.

## Prerequisites

- Required previous coverage IDs or run packets: GLB_04.
- Required app/data state: Authenticated map with 12 visible tracks.
- Required browser context: Desktop Chromium context.

## Allowed Mutations

- Allowed: Open Admin and inspect tiles.
- Not allowed: Change server data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ADM_01 | Opened Admin from the main map toolbar and captured the tile grid. | Admin dialog opens; the tab/list entries are reachable and usable. | Admin workspace opened over the 12-track map. The grouped tiles exposed Upload, Jobs, Freshness, Garmin Sync, Log, Helpers, About, Settings, Session, and Attribution with `Open ...` aria labels. | PASS | [assets/ADM_01-admin-home.webp](../assets/ADM_01-admin-home.webp); [assets/ADM_01-admin-tabs.txt](../assets/ADM_01-admin-tabs.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ADM_01-admin-home.webp](../assets/ADM_01-admin-home.webp) | Admin workspace tile grid. |
| [assets/ADM_01-admin-tabs.txt](../assets/ADM_01-admin-tabs.txt) | Tile labels, aria labels, and 12-track context. |

## Screenshot Evidence

**Admin workspace tile grid.**

![Admin workspace tile grid.](../assets/ADM_01-admin-home.webp)

## Timings

| Step | Timing |
|---|---:|
| Open Admin and inspect tiles | ~10 s |

## Handoff Notes

- Completed: ADM_01 terminal as `PASS`.
- Remaining unfinished coverage: Continue with ADM_02.
- Blocked or not applicable: None.
- State left for the next packet: Server data unchanged.
