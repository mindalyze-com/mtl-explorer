# Packet: ACC_05

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md` coverage accounting section.
- Coverage ID or run packet: ACC_05
- In scope: Record constraints explicitly instead of silently collapsing checks.
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
| ACC_05 | Recorded terminal/resumable status semantics and constraint handling. | Blocked/not-applicable cases must include a concrete reason and unblock path; unfinished executable coverage must remain resumable. | Status semantics are recorded in run-state/process evidence before functional coverage begins. | PASS | [assets/ACC_05-status-semantics.txt](../assets/ACC_05-status-semantics.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_05-status-semantics.txt](../assets/ACC_05-status-semantics.txt) | Accounting evidence for ACC_05. |

## Screenshot Evidence

No screenshot required for this accounting packet.

## Timings

| Step | Timing |
|---|---:|
| Accounting check | <1 min |

## Handoff Notes

- Completed: ACC_05.
- Remaining unfinished coverage: DAT_01 onward.
- Blocked or not applicable: none.
- State left for the next packet: run remains resumable with the next coverage ID set in `run-state.md`.
