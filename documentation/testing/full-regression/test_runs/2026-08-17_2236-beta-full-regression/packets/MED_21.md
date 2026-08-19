# Packet: MED_21

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_21
- In scope: 100,000-media/300-activity query plans, HTTP timings, indexed projections, and no runtime full-library interpolation.
- Out of scope: Six-item functional behavior.

## Prerequisites

- Required previous coverage IDs or run packets: MED_20.
- Required app/data state: At least 100,000 synthetic media rows and 300 activity rows.
- Required browser context: Map bounds and Track Details timeline.

## Allowed Mutations

- Allowed: Read-only row/index inspection and scale-query timing on a packaged isolated fixture.
- Not allowed: Inventing direct production-table seed writes that bypass product ingestion and pollute remaining coverage.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_21 | Audited live scale and relevant indexes before any EXPLAIN ANALYZE claim. | 100,000 media and 300 activities are available for indexed map/timeline plans and HTTP timing. | Run has 6 media and 17 stored activities; required isolated scale seed is absent. Intended spatial/timeline indexes exist, but scale behavior cannot be measured. | BLOCKED | [assets/MED_21-scale-prerequisite.txt](../assets/MED_21-scale-prerequisite.txt) |

## Issues

- Missing required scale fixture, not a product assertion.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_21-scale-prerequisite.txt](../assets/MED_21-scale-prerequisite.txt) | Exact row counts, required scale, and relevant index definitions. |

## Screenshot Evidence

Not applicable to query-plan and scale timing evidence.

## Timings

| Step | Timing |
|---|---:|
| Prerequisite and index audit | Under 3 s |

## Handoff Notes

- Completed: Safe prerequisite/index audit.
- Remaining unfinished coverage: None for MED_21.
- Blocked or not applicable: 100,000-row/300-activity seed absent.
- State left for the next packet: Six-photo functional dataset intact.
