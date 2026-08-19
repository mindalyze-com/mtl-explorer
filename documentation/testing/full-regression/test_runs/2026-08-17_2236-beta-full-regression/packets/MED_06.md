# Packet: MED_06

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_06
- In scope: Media count/status/freshness baseline, matching GPX import, six-JPEG copy, Admin rescan, settled indexing/correlation, freshness reload, and preserved recovery sources.
- Out of scope: Detailed media browse/edit/delete behavior in later MED packets.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_08, FMT_02.
- Required app/data state: Zero media; six generated JPEGs and matching GPX preserved outside watchers.
- Required browser context: Signed-in Admin Processing, Data status, Maintenance, and Statistics.

## Allowed Mutations

- Allowed: Import matching GPX, copy six JPEGs, trigger rescan, use the documented API only as a fallback after recording a UI failure, and accept freshness reload.
- Not allowed: Lose or mutate preserved source fixtures.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_06 | Repeated Admin `Rescan Media` by pointer and keyboard on the matching beta build at desktop and mobile sizes. | Admin action queues MEDIA indexing; all six files and correlations complete without failure. | Pointer activation issued the expected MEDIA rescan request and completed 6/6. The earlier no-request result did not reproduce; keyboard automation did not synthesize a native click and was not treated as product evidence. | REJECTED | [retest](../assets/MED_06-retest.txt); [desktop](../assets/MED_06-rejected-desktop.webp); [mobile](../assets/MED_06-rejected-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|
| FR-001 | P1 | Admin `Rescan Media` is a no-op. | Open Admin > Maintenance with unindexed watched media; activate `Rescan Media` by click or keyboard. | A MEDIA rescan request is queued and user feedback appears. | No request, feedback, or state change occurs; the documented API endpoint works when called separately. | [assets/MED_06-setup.txt](../assets/MED_06-setup.txt) | Blocks end-user recovery when filesystem watching misses media changes, including the WSL2 case described by the same page. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_06-setup.txt](../assets/MED_06-setup.txt) | Baseline, exact UI failure, API fallback, settled indexing/correlation, freshness, and statistics evidence. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Preserved source fixture names, hashes, timestamps, and position modes. |

## Screenshot Evidence

Blocked by ACC_04; direct DOM, route, server-request, indexer, and freshness evidence is recorded.

## Timings

| Step | Timing |
|---|---:|
| Matching GPX indexing | About 10 s |
| UI rescan attempts and diagnosis | About 3 min |
| API fallback, six-file indexing, and correlation | Under 3 s |
| Freshness reload and UI verification | About 20 s |

## Handoff Notes

- Completed: All six media fixtures and matching track are indexed and preserved for later media coverage.
- Remaining unfinished coverage: None for MED_06; FR-001 remains open.
- Blocked or not applicable: Screenshot evidence remains blocked under ACC_04.
- State left for the next packet: Seventeen indexed tracks total, fourteen default-visible tracks, six indexed/correlated media, client in sync.

## Remediation Verification

- Finding FR-001 is `REJECTED`: the exact beta UI queued the documented endpoint by pointer on desktop and mobile.
- No product change was made. Evidence is linked in the action row.
