# Packet: SGN_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SGN_09
- In scope: Verify browser back/forward navigation between app views works without errors.
- Out of scope: Deep-link reload behavior for every route.

## Prerequisites

- Required previous coverage IDs or run packets: SGN_08.
- Required app/data state: Authenticated app shell with 11 tracks loaded.
- Required browser context: desktop browser.

## Allowed Mutations

- Allowed: Open a fresh tab and navigate between existing app views.
- Not allowed: Change data, preferences, or server state.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SGN_09 | From a fresh app tab, clicked Stats, clicked Filter, then used browser Back and Forward. | Back/forward history restores the matching view for the URL without UI errors. | Stats and Filter clicks worked. Browser Forward returned to Filter correctly, but browser Back changed the URL to `/mtl/stats` while the UI still showed Filter active and filter content. Console warnings/errors were empty. | FIXED | [assets/SGN_09-back-forward.txt](../assets/SGN_09-back-forward.txt); [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| SGN-09-P2 | P2 | Browser Back can desynchronize URL and active view. | Open `/mtl/`, click Stats, click Filter, then use browser Back. | URL `/mtl/stats` should show the Stats view active with matching content. | Address bar changed to `/mtl/stats`, but Filter remained active and filter content stayed visible. | [assets/SGN_09-back-forward.txt](../assets/SGN_09-back-forward.txt) | Users relying on browser history can land on misleading URLs and the wrong app panel, which can confuse navigation and sharing/debugging. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt) | Local implementation and verification evidence for FIXED status. |
| [assets/SGN_09-back-forward.txt](../assets/SGN_09-back-forward.txt) | Clean navigation sequence, URL/view observations, and console status. |

## Screenshot Evidence

No screenshot asset was captured for this packet; browser screenshot capture was unstable after SGN_07, so direct DOM and URL evidence is recorded in the text asset.

## Timings

| Step | Timing |
|---|---:|
| Clean back/forward navigation check | ~2 min |

## Handoff Notes

- Fix status: FIXED locally: browser Back/Forward route sync explicitly opens the URL target panel. Evidence: [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt).

- Completed: SGN_09.
- Remaining unfinished coverage: MAP_01 onward.
- Blocked or not applicable: none.
- State left for the next packet: A browser tab is on `/mtl/filter`; use a fresh tab or direct root load for MAP_01.
