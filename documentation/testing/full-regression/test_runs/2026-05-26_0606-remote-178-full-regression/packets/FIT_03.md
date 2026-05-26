# Packet: FIT_03

## Scope

- Coverage source: `documentation/testing/frontend-regression-test-plan.md`
- Coverage ID or run packet: FIT_03
- In scope: FIT conversion/import/details/download flow.
- Out of scope: non-FIT format coverage.

## Prerequisites

- Required previous coverage IDs or run packets: DAT_05, DEL flow complete, app running.
- Required app/data state: Garmin `Activity.fit` staged before FIT_01.
- Required browser context: desktop signed-in browser; API token used only because in-app browser downloads are unsupported.

## Allowed Mutations

- Allowed: copy FIT to watched folder, use visible UI controls, download files from app endpoints.
- Not allowed: product source edits or workarounds that alter conversion behavior.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| FIT_03 | Opened FIT-backed track details and switched Overview, Graphs, Quality, Related, and Events. | FIT detail overview, graphs, quality, events, related tracks, mini-map, and point surfaces render like GPX-backed tracks. | FIT #100005 opened; overview, graphs, quality, related, and events tabs rendered; mini-map was visible; events tab correctly showed no track events. | PASS | `assets/FIT_03-fit-detail-overview.webp`, `assets/FIT_03-fit-graphs.webp`, `assets/FIT_03-fit-quality.webp`, `assets/FIT_03-fit-related.webp`, `assets/FIT_03-fit-events.webp` |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Release impact |
|---|---|---|---|---|---|---|---|

## Evidence Files

| File | Purpose |
|---|---|
| assets/FIT_03-fit-detail-overview.webp | FIT detail overview. |
| assets/FIT_03-fit-graphs.webp | FIT graphs tab. |
| assets/FIT_03-fit-quality.webp | FIT quality tab. |
| assets/FIT_03-fit-related.webp | FIT related tab. |
| assets/FIT_03-fit-events.webp | FIT events tab. |

## Timings

| Step | Timing |
|---|---:|
| FIT step | <1m |

## Handoff Notes

- Completed: FIT_03 terminal PASS.
- Remaining unfinished coverage: continue queue.
- Blocked or not applicable: none unless stated.
- State left for the next packet: dataset contains three remaining GPX tracks plus FIT track #100005.
