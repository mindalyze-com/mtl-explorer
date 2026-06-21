# Packet: ACC_04

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` coverage accounting section.
- Coverage ID or run packet: ACC_04
- In scope: Capture compact screenshots for working functions and failures.
- Out of scope: functional UI behavior outside this accounting rule.

## Prerequisites

- Required previous coverage IDs or run packets: RUN_SETUP and preceding ACC packet(s).
- Required app/data state: run-state initialized.
- Required browser context: none beyond existing setup evidence.

## Allowed Mutations

- Allowed: update this packet and run-state.
- Not allowed: collapse or skip later coverage IDs.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_04 | Verified compact WebP screenshot workflow using the setup login screenshot and recorded evidence policy for later packets. | Representative working UI functions and failures should have compact evidence, with WebP screenshots under 85 KB where used. | RUN_SETUP login screenshot is WebP and under 85 KB; later UI packets will add representative compact screenshots as they execute. | PASS | [assets/ACC_04-evidence-policy.txt](../assets/ACC_04-evidence-policy.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_04-evidence-policy.txt](../assets/ACC_04-evidence-policy.txt) | Accounting evidence for ACC_04. |

## Screenshot Evidence

No screenshot required for this accounting packet.

## Timings

| Step | Timing |
|---|---:|
| Accounting check | <1 min |

## Handoff Notes

- Completed: ACC_04.
- Remaining unfinished coverage: ACC_05 onward.
- Blocked or not applicable: none.
- State left for the next packet: run remains resumable with the next coverage ID set in `run-state.md`.
