# Packet: ACC_02

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` coverage accounting section.
- Coverage ID or run packet: ACC_02
- In scope: Do not pass parent sections while children are skipped.
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
| ACC_02 | Recorded one-packet-per-ID and no parent-section-collapse rule for this run. | Each child coverage ID must have its own terminal packet evidence. | Run-state contains individual child rows only; parent headings are not used as terminal substitutes. | PASS | [assets/ACC_02-packet-boundary.txt](../assets/ACC_02-packet-boundary.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_02-packet-boundary.txt](../assets/ACC_02-packet-boundary.txt) | Accounting evidence for ACC_02. |

## Screenshot Evidence

No screenshot required for this accounting packet.

## Timings

| Step | Timing |
|---|---:|
| Accounting check | <1 min |

## Handoff Notes

- Completed: ACC_02.
- Remaining unfinished coverage: ACC_03 onward.
- Blocked or not applicable: none.
- State left for the next packet: run remains resumable with the next coverage ID set in `run-state.md`.
