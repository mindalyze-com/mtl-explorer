# Packet: PLN_09

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: PLN_09
- In scope: routing-data missing/unavailable state, if present.
- Out of scope: inducing artificial server-side BRouter failure.

## Prerequisites

- Required previous coverage IDs or run packets: PLN_02.
- Required app/data state: planner open with BRouter status available.
- Required browser context: signed-in desktop browser.

## Allowed Mutations

- Allowed: open BRouter status detail.
- Not allowed: break the routing service or delete routing data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| PLN_09 | Checked planner BRouter status after successful route computation. | If BRouter is missing data for an area, UI shows a clear segment downloading/unavailable state instead of an unhandled error. | Not applicable in this configured run: tested planner routes computed successfully and BRouter status showed ready, running `yes`, 3 segments on disk, queued `0`. No missing-data state occurred. | NOT APPLICABLE | `assets/PLN_09-brouter-ready.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/PLN_09-brouter-ready.webp | BRouter ready status detail for this run. |

## Timings

| Step | Timing |
|---|---:|
| BRouter status check | <2m |

## Handoff Notes

- Completed: PLN_09 terminal NOT APPLICABLE.
- Remaining unfinished coverage: continue with PLN_10.
- Blocked or not applicable: missing routing-data state was not present in this run.
- State left for the next packet: planner open with BRouter status expanded.
