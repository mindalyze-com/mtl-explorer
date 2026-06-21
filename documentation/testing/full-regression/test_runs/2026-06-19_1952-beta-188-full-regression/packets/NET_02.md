# Packet: NET_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: NET_02
- In scope: Recoverable UI state during flaky/no API connectivity.
- Out of scope: Installed-PWA offline reload behavior, covered by NET_01 applicability.

## Prerequisites

- Required previous coverage IDs or run packets: NET_01.
- Required app/data state: Authenticated isolated browser context.
- Required browser context: Desktop Chromium context with request interception.

## Allowed Mutations

- Allowed: Clear browser-local CacheStorage/IndexedDB in an isolated context, abort `/mtl/api/**`, reload the page.
- Not allowed: Change server-side data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| NET_02 | Logged in, cleared local browser caches except JWT, aborted `/mtl/api/**`, and reloaded the app. | A flaky connection shows recoverable error states, not a blank screen. | The app did not go blank and the map shell/canvas stayed visible, but it showed only `0 Tracks` with no visible retry/offline/error message. `retryVisible=false` and recoverable message scan was false. | FIXED | [assets/NET_02-network-recovery.txt](../assets/NET_02-network-recovery.txt); [assets/NET_02-network-recovery.webp](../assets/NET_02-network-recovery.webp); [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| NET-02-P2 | P2 | API outage without cached tracks renders an empty map without a recoverable message. | In an authenticated browser context, clear CacheStorage/IndexedDB while keeping the JWT, abort `/mtl/api/**`, and reload `/mtl/`. | MTL Explorer shows an actionable offline/retry/error state while keeping the shell usable. | The shell stayed usable, but only `0 Tracks` appeared; no retry/offline/error message was visible. | [assets/NET_02-network-recovery.txt](../assets/NET_02-network-recovery.txt); [assets/NET_02-network-recovery.webp](../assets/NET_02-network-recovery.webp) | Users on a flaky connection can mistake an outage for an empty archive and have no visible recovery action. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt) | Local implementation and verification evidence for FIXED status. |
| [assets/NET_02-network-recovery.txt](../assets/NET_02-network-recovery.txt) | Request-abort setup, UI state, and console evidence. |
| [assets/NET_02-network-recovery.webp](../assets/NET_02-network-recovery.webp) | Empty map shell after API abort. |

## Screenshot Evidence

![Network abort empty map](../assets/NET_02-network-recovery.webp)

## Timings

| Step | Timing |
|---|---:|
| API abort recovery capture | 14.5 s cumulative |

## Handoff Notes

- Fix status: FIXED locally: no-cache API outage keeps actionable retry/error state instead of silent 0 Tracks. Evidence: [assets/FIXED-issues-local-verification.txt](../assets/FIXED-issues-local-verification.txt).

- Completed: NET_02 marked FIXED with local implementation evidence.
- Remaining unfinished coverage: NET_03 onward at packet creation time.
- Blocked or not applicable: None.
- State left for the next packet: Isolated network-abort context closed; server-side data unchanged.
