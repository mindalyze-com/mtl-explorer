# Packet: MED_40

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_40
- In scope: Browser reload and app-container restart stability for both videos across activity/main-map entry, posters, navigation, playback, locations/origins, ranges, downloads, index state, and console/app errors.
- Out of scope: New archival-codec conversion fixture covered by MED_41.

## Prerequisites

- Required previous coverage IDs or run packets: MED_36-39 with manual-location cleanup complete.
- Required app/data state: Eight-item fixture; no manual video rows; empty work queues.
- Required browser context: Authenticated track/activity and main-map flows.

## Allowed Mutations

- Allowed: Browser reload, restart of exactly Compose service `app`, ephemeral playback/navigation, temporary authenticated downloads.
- Not allowed: Restart/recreate database or support services; change fixture data or image.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_40 | Reloaded the app, reopened both activity videos, restarted exactly the app service, reloaded again, reopened both videos from activity and main-map cluster, replayed them, verified locations/origins/index/work state, repeated serving/checksums, and scanned app/browser logs. | Both videos remain stable across reload/restart with no failed index row, blank viewer, serving/download regression, origin drift, or uncaught error. | Both entry paths, posters, navigation, native Play, locations/origins, 200/206/416, downloads, and checksums remained stable. MEDIA stayed 2/2 successful, queues/manual rows stayed empty, image/digest stayed exact, and app/browser error scans were empty. | PASS | [assets/MED_40-restart-stability.txt](../assets/MED_40-restart-stability.txt) |

## Issues

No new issue. The known MTL-FR-017 map-filmstrip label/badge defect remained reproducible and is already owned by MED_36.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_40-restart-stability.txt](../assets/MED_40-restart-stability.txt) | Exact reload/restart identity, readiness, both UI entry paths, database state, serving/checksum, log scans, and cleanup. |

## Screenshot Evidence

Live desktop screenshots confirmed the reloaded activity viewers, post-restart Bern cluster, MOV map viewer, and MP4 map viewer. ACC_04 prevents durable local screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Browser reload settlement | About 1.8 s |
| App restart to HTTP 200 | About 10 s |
| Viewer re-entry transitions | 220-300 ms each |

## Handoff Notes

- Completed: Reload and actual app restart, both entry paths, playback/poster/navigation/location/origin persistence, serving/download/checksum, index/work queues, logs, and temporary cleanup.
- Remaining unfinished coverage: None for MED_40.
- Blocked or not applicable: Durable screenshots remain unavailable under ACC_04.
- State left for the next packet: Main-map MP4 viewer open; app container freshly restarted on the requested image; baseline 8/8/8 and queues 0/0.
