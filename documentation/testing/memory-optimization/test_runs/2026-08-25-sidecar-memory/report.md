# Sidecar Memory Optimization

Date: 2026-08-25

## Applied limits

| Container | Validated hard limit | 20% threshold | Compose hard limit | Reservation | Swap |
| --- | ---: | ---: | ---: | ---: | ---: |
| BRouter | 384 MiB | 460.8 MiB | 512 MiB | 192 MiB | disabled |
| Location search | 96 MiB | 115.2 MiB | 128 MiB | 64 MiB | disabled |
| Map server | 96 MiB | 115.2 MiB | 128 MiB | 64 MiB | disabled |

Compose limits exceed the validated values by at least 20 percent and are rounded up to conventional allocations. BRouter completed routes up to about 1,500 km with its 128 MiB Java heap. An uncached Barcelona-Munich request through the authenticated MTL Explorer planner endpoint returned a 1,487.9 km route with 42,049 geometry points in about 60 seconds. The app-side Compose timeout is therefore 310 seconds, just above BRouter's existing 300-second route ceiling. Location search passed health, lookup, and autocomplete checks.

## Map server image and data safety

The former `wauwau0977/mytraillog-maps:1.70` container ran ClickHouse even though the current map server only needs nginx, the PMTiles CLI, and its Python orchestrator. ClickHouse used about 469 MiB RSS. The current `docker-maps` image removes that process.

The live container was replaced without changing its `mtl-maps-data:/data` mount. The stopped former container remains available as `map-server-legacy-clickhouse-20260825` for rollback. The new container reused the existing 125.6 GB archive and skipped downloading and extracting it.

Before and after the image change, the archive had the same size, inode, and modification time. The existing low-zoom archive checksum and both map symlink targets were also unchanged. `pmtiles show` read the archive metadata, and extracting a Zürich vector tile returned a valid 53,642-byte gzip MVT tile.

## Map memory tests

- 64 MiB served range requests but was killed while extracting the zoom 0-6 overview. It was rejected.
- 96 MiB completed the zoom 0-6 extraction and is the validated floor.
- The 20 percent threshold is 115.2 MiB; the final Compose limit is rounded up to 128 MiB.
- At 116 MiB, the live server completed 500 concurrent 64 KiB range requests and 100 concurrent 1 MiB range requests. Every response was HTTP 206 with the expected size. Health stayed green with no restart or OOM event.
- Zoom 8 extraction produced about 519 MB of temporary output and exceeded 116 MiB. All supplied Compose files therefore use `LOWZOOM_MAXZOOM=6`.

The cgroup reached its hard limit during extraction and stress because Linux filled the remaining space with reclaimable file cache. Docker reported about 33 MiB active memory after the live test.

## Browser check

The local UI loaded its map configuration, reported the local archive and BRouter as ready, changed scale through the zoom controls, opened the planner, and returned local location-search results for Zürich. The in-app browser's network filter blocked PMTiles resource loading, so the canvas could not be used as visual tile evidence. The authenticated Java map proxy was therefore checked separately and returned the requested archive range as HTTP 206 with the correct length. The live map server also served the direct range stress and decoded vector tile described above.
