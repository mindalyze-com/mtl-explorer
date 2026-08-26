# Packet: MED_11

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_11
- In scope: Pan away/back, cluster-level zooms, hard reload, immediate viewport revisit, no stale/duplicate/deleted pins, and non-cached bounds response.
- Out of scope: Database audit retention covered by MED_12.

## Prerequisites

- Required previous coverage IDs or run packets: MED_10 post-delete six-item UI/bounds state.
- Required app/data state: Six active media; deleted source backups retained outside watched storage.
- Required browser context: Main map at the recorded 100 m Bern viewport.

## Allowed Mutations

- Allowed: Map pan/zoom/search, hard reload, cluster chooser, and repeated read-only bounds requests.
- Not allowed: Data/config/filter mutation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_11 | Panned away by focused-map keyboard input, zoomed 100m->500m->100m, searched/panned back to Bern, opened the 6-photo cluster, hard-reloaded, reopened it immediately, and repeated identical bounds requests. | Deleted pins never return, remaining pins are not duplicated, and bounds data is not cached. | Away view had no cluster; return and post-hard-reload choosers each showed exactly 6/6. Both identical live requests returned 200 with `Cache-Control: no-store`, exact six-ID bodies, and no deleted ID. | PASS | [assets/MED_11-reload-cache.txt](../assets/MED_11-reload-cache.txt) |

## Issues

No new issue.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_11-reload-cache.txt](../assets/MED_11-reload-cache.txt) | Pan/zoom/search/hard-reload results, cluster counts, headers, body hash, and exact IDs. |

## Screenshot Evidence

Live desktop inspection confirmed away/return, both zoom scales, and both six-photo chooser states. ACC_04 prevents durable screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Away pan settlement | About 0.9 s |
| Each zoom pair | About 0.4 s |
| Bern return | About 0.9 s |
| Hard reload settlement | About 2.4 s |

## Handoff Notes

- Completed: Pan/zoom/return/hard-reload stability, exact cluster cardinality, and no-store bounds proof.
- Remaining unfinished coverage: None for MED_11.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: Post-hard-reload Bern chooser open with exactly 6/6 photos; database remains 6/6/6, queues 0/0.
