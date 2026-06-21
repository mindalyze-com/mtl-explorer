# Packet: DEL_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: DEL_04
- In scope: Verify remaining imported tracks still display and open correctly after deleting two source files.
- Out of scope: Direct stale deleted-track API URL behavior; DEL_05 explicitly excludes that as pass/fail criteria.

## Prerequisites

- Required previous coverage IDs or run packets: DEL_02.
- Required app/data state: two imported source files removed and delete processing settled.
- Required browser context: authenticated desktop browser context.

## Allowed Mutations

- Allowed: Reload browser state and navigate read-only user-facing surfaces.
- Not allowed: Delete additional files or alter remaining tracks.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| DEL_04 | Searched remaining source filenames in the track browser and opened remaining Mosel track #100003 details after deletion. | Remaining imported tracks still display and open correctly. | Jura, Mosel, and Lannion filename searches each return a visible row, and Mosel #100003 opens Track Details with map and Overview content in the 3-track post-delete state. | PASS | [assets/DEL_03-deletion-surfaces.txt](../assets/DEL_03-deletion-surfaces.txt); [assets/DEL_03-browser-search-results.txt](../assets/DEL_03-browser-search-results.txt); [assets/DEL_04-remaining-detail-open.webp](../assets/DEL_04-remaining-detail-open.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/DEL_03-deletion-surfaces.txt](../assets/DEL_03-deletion-surfaces.txt) | Evidence for DEL_04. |
| [assets/DEL_03-browser-search-results.txt](../assets/DEL_03-browser-search-results.txt) | Evidence for DEL_04. |
| [assets/DEL_04-remaining-detail-open.webp](../assets/DEL_04-remaining-detail-open.webp) | Evidence for DEL_04. |

## Screenshot Evidence

![Remaining detail after delete](../assets/DEL_04-remaining-detail-open.webp)

## Timings

| Step | Timing |
|---|---:|
| Deletion surface verification | ~3 min |

## Handoff Notes

- Completed: DEL_04.
- Remaining unfinished coverage: DEL_05 onward.
- Blocked or not applicable: none.
- State left for the next packet: three GPX tracks remain after deletion.
