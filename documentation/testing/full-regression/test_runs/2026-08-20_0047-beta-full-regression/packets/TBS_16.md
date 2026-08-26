# Packet: TBS_16

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TBS_16
- In scope: Period/undated mosaics, kind filters, ordering, pagination/errors, viewer navigation, and scoped Open activity.
- Out of scope: Planner coverage.

## Prerequisites

- Required previous coverage IDs or run packets: TBS_15.
- Required app/data state: Eight indexed media items in a non-empty period.
- Required browser context: Trends Media chart, mosaic, and shared viewer.

## Allowed Mutations

- Allowed: Open stack, switch filters, and navigate viewer.
- Not allowed: Fabricate missing Undated or 60+ item state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| TBS_16 | Open period/undated stacks, test filters/order/pages/errors/viewer and scoped Open activity. | Every mosaic/viewer/scoped action is available and correct. | The original reachable mosaic/viewer paths passed. The fixed local worktree restores the required All indexed / Track related scope path; the original fixture still has no Undated or 60+ item branch. | FIXED | [original](../assets/TBS_16-media-mosaic-viewer.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-009-fix-local-desktop.webp); [mobile](../assets/MTL-FR-009-fix-local-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MTL-FR-009 | P2 | Required Track related / All indexed media scope controls are absent. | Open Trends Media and a period mosaic. | Scoped Open activity flow is available in Track related. | Fixed locally: All indexed and Track related replace the obsolete three-mode flow. | [original](../assets/TBS_16-media-mosaic-viewer.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt) | FIXED in the local worktree. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TBS_16-media-mosaic-viewer.txt](../assets/TBS_16-media-mosaic-viewer.txt) | Mosaic totals/filters/order, viewer navigation, and unreachable subchecks. |

## Screenshot Evidence

![Desktop media scope and viewer retest](../assets/MTL-FR-009-fix-local-desktop.webp)

![Mobile media scope and viewer retest](../assets/MTL-FR-009-fix-local-mobile.webp)

## Fix Record

- MTL-FR-009 is fixed by the two-scope Statistics flow.
- The original packet's missing-data branches remain fixture limitations, not product failures.
- See [local evidence](../assets/MTL-FR-005-021-fix-local.txt).

## Timings

| Step | Timing |
|---|---:|
| Period mosaic filters and order | 3 min |
| Shared viewer navigation | 2 min |
| Missing/unreachable subcheck audit | 1 min |

## Handoff Notes

- Completed: Non-empty period mosaic and shared viewer coverage.
- Remaining unfinished coverage: None; missing frozen scope/Undated path yields terminal FAIL; 60+ and injected-error paths are unreachable in this dataset.
- Blocked or not applicable: 60-item pagination and injected error recovery were not reachable.
- State left for the next packet: Media viewer open; one-track geo filter still active and must be reset before Planner.
