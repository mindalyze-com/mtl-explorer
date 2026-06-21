# Packet: SYN_06

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: SYN_06
- In scope: Verify logging out and back in does not repeatedly re-trigger automatic data refresh.
- Out of scope: General authentication coverage already handled in SGN packets.

## Prerequisites

- Required previous coverage IDs or run packets: SYN_05.
- Required app/data state: Authenticated desktop context with a known applied freshness token.
- Required browser context: Desktop Chrome context against the remote target.

## Allowed Mutations

- Allowed: Trigger a safe GPS rescan to create a stale applied token, log out, and log back in with README credentials.
- Not allowed: Add or delete track files.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| SYN_06 | Set the applied token, triggered a GPS rescan to make the client stale, logged out, confirmed the login form, logged back in, and observed the map for 50 seconds. | Login may reconcile freshness once but must not repeatedly reload or keep re-triggering a freshness refresh loop. | Login form was shown after logout; after logging back in, the map loaded with `15 Tracks`, no freshness banner loop appeared, and the applied token equaled the server token after the observation window. | PASS | [assets/SYN_06-after-relogin.webp](../assets/SYN_06-after-relogin.webp); [assets/SYN-live-results.txt](../assets/SYN-live-results.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/SYN_06-after-relogin.webp](../assets/SYN_06-after-relogin.webp) | Stable map after logout/login freshness reconciliation. |
| [assets/SYN-live-results.txt](../assets/SYN-live-results.txt) | Login visibility, token equality, and no-banner summary. |

## Screenshot Evidence

![Stable map after relogin](../assets/SYN_06-after-relogin.webp)

## Timings

| Step | Timing |
|---|---:|
| Rescan, logout/login, and 50-second observation | ~3 min |

## Handoff Notes

- Completed: SYN_06 passed.
- Remaining unfinished coverage: SYN_07.
- Blocked or not applicable: None.
- State left for the next packet: No file mutation in this packet.
