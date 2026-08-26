# Packet: TRD_15

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: TRD_15
- In scope: Stats and Filter origins, Close return state, browser history, narrow mobile, and direct-link Close.
- Out of scope: Other detail behavior covered by TRD_01-TRD_14.

## Prerequisites

- Required previous coverage IDs or run packets: TRD_01-TRD_14.
- Required app/data state: Track 100005 included in the nine-track result.
- Required browser context: Authenticated fresh-tab origin checks.

## Allowed Mutations

- Allowed: Search lists, navigate routes, use Close, Back, and Forward, and open a fresh tab.
- Not allowed: Change track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| TRD_15 | Open track 100005 from Stats Tracks and Filter Review, close it, use Back/Forward, open a direct link, and attempt narrow mobile. | Close restores each origin and list state; history and direct-link flows work on desktop/mobile. | Readiness-gated replay scoped Close to the single open nonbackgrounded Track Details sheet. One activation restored Filter Review, its sample.igc search, and route on desktop and mobile. The original finding targeted an ambiguous stacked-sheet Close. | REJECTED | [original](../assets/TRD_15-origin-flows.txt); [retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-006-008-fix-local-desktop.webp); [mobile](../assets/MTL-FR-006-008-fix-local-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| MTL-FR-006 | P2 | Track Details Close does not return to Filter Review tracks. | Main map -> Filter -> Review tracks -> search Track 100005 -> open row -> Close. | Filter Review reopens with its search/list state. | Not reproduced with the foreground Track Details Close: one activation restored `/mtl/filter` and retained Review/search state at both viewports. | [original](../assets/TRD_15-origin-flows.txt); [retest](../assets/MTL-FR-005-021-fix-local.txt) | REJECTED; no product defect established. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/TRD_15-origin-flows.txt](../assets/TRD_15-origin-flows.txt) | Origin, Close, history, direct-link, and mobile-constraint matrix. |

## Screenshot Evidence

![Desktop Filter Review retest](../assets/MTL-FR-006-008-fix-local-desktop.webp)

![Mobile Filter Review retest](../assets/MTL-FR-006-008-fix-local-mobile.webp)

## Remediation Verification

- MTL-FR-006 is `REJECTED`; no production change was made.
- Every stacked sheet retains a labelled Close in the DOM. Automation must target the open nonbackgrounded sheet before its Close control.
- The focused stacked-sheet regression and exact desktop/mobile replay pass. See [local evidence](../assets/MTL-FR-005-021-fix-local.txt).

## Timings

| Step | Timing |
|---|---:|
| Desktop origins, history, direct link, and mobile capability check | 15 min |

## Handoff Notes

- Completed: Stats origin, Filter origin, browser history, and direct-link flows.
- Remaining unfinished coverage: None; this packet is terminal FAIL.
- Blocked or not applicable: Narrow-mobile child check only; browser exposes no viewport override.
- State left for the next packet: Main map restored; nine-track data unchanged.
