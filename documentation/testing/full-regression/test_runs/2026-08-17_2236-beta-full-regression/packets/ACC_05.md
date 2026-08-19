# Packet: ACC_05

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: ACC_05
- In scope: Explicitly record constraints instead of hiding skipped checks.
- Out of scope: Deciding later constraint outcomes before direct attempts.

## Prerequisites

- Required previous coverage IDs or run packets: ACC_04.
- Required app/data state: Run-state and packet evidence available.
- Required browser context: None.

## Allowed Mutations

- Allowed: Audit constraint records.
- Not allowed: Convert unfinished work to terminal status without a concrete constraint.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| ACC_05 | Audited active and resolved constraints in RUN_SETUP, shared facts, and ACC_04, including exact causes and unblock paths. | Time, tooling, viewport, data, permission, and environment constraints are explicit and not collapsed into parent PASS results. | Credential, Docker prerequisite, plain-HTTP geolocation, and screenshot-tool constraints are explicitly recorded; future packet constraints are governed by the same rule. | PASS | [assets/ACC_05-constraint-accounting.txt](../assets/ACC_05-constraint-accounting.txt) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| [assets/ACC_05-constraint-accounting.txt](../assets/ACC_05-constraint-accounting.txt) | Constraint examples and enforcement rule. |

## Screenshot Evidence

Not applicable; this is a coverage-accounting packet.

## Timings

| Step | Timing |
|---|---:|
| Constraint audit | <1 s |

## Handoff Notes

- Completed: Constraint accounting is explicit and auditable.
- Remaining unfinished coverage: None for ACC_05.
- Blocked or not applicable: ACC_04 remains terminally blocked as recorded in its own packet.
- State left for the next packet: Begin required public data setup at DAT_01.
