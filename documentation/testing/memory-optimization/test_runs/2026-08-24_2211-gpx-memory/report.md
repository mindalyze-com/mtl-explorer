> **RESULT: PASS - GPX allocation was reduced by about 91%, all public demo GPX files indexed under a 768 MiB app limit, and the configured limits are at least 20% above the validated floors.**

# MTL Explorer GPX memory investigation

Date: 2026-08-24

## Question

Determine why GPX import appeared to require about 1.5 GiB, verify the cause with Java Flight Recorder and Native Memory Tracking, reduce the memory, and confirm correctness under hard Docker limits. Include Java, PostgreSQL, image processing, video processing, the public demo workload, and representative large GPX files.

Private GPX files were used only as opaque local stress input. This report records counts and aggregate measurements only. It contains no private path, filename, coordinate, metadata, copy, or derived fixture.

## What the recording found

The original 1.5 GiB behavior was not a retained GPX heap leak. A real continuous JFR captured the large-file import, and a forced full collection left only 110.8 MiB live on the Java heap.

The dominant cost was transient allocation:

- GeoTools and GeographicLib distance calculation accounted for about 65% of sampled allocation.
- Stop detection repeatedly allocated, copied, and sorted arrays, accounting for another 10-15%.
- The JVM also carried fixed native costs: about 122 MiB metaspace, 42 MiB code cache, 42 MiB G1 state, 28 MiB symbols, thread stacks, and native allocator overhead.
- Container memory additionally includes mapped files and filesystem cache. RSS therefore did not represent retained GPX objects.

The baseline JFR is at `/tmp/mtl-memory-baseline.HONLTI/logs/mtl-baseline-final.jfr`. The optimized recording is at `/tmp/mtl-memory-optimized-jfr.9hofrl/logs/optimized-final.jfr`. These are local, temporary diagnostic files and are not repository artifacts.

## Changes

- Replaced the GeoTools/GeographicLib inner-loop distance implementation with allocation-free WGS84 Vincenty inverse calculation. The current 37,692-byte GeographicLib 2.1 remains as a direct dependency and supplies the exact ellipsoidal answer only when Vincenty cannot converge, such as antipodal inputs.
- Replaced GeoTools Web Mercator conversion with the standard direct EPSG:3857 formula.
- Removed GeoTools, its EPSG database, its startup warm-up, and unused JSoup from the server runtime.
- Streamed GPX files through JPX instead of reading and cleaning a second full XML string in memory.
- Removed avoidable track/segment/point list copies.
- Reused per-worker stop-detection scratch arrays and sorted only their active prefixes.
- Reduced default indexer and exploration concurrency to one worker.
- Bounded primary and read-only database pools, Tomcat threads, and scheduler threads.
- Changed the constrained-container collector from ZGC to G1, limited the heap to 31.25% of the app cgroup, and limited active processors and thread stacks.
- Limited ImageMagick concurrency and memory, and limited FFmpeg decoder, filter, and encoder threads.
- Tuned PostgreSQL for the small container while retaining enough WAL capacity for imports.

Removing GeoTools reduced the production image from 1,049,287,937 to 1,033,966,136 bytes.

## GeoTools compatibility gate

The replacement was checked against GeoTools itself before GeoTools was removed:

1. GeoTools 35.1 and the original method bodies were temporarily restored.
2. That implementation generated a fixed oracle from synthetic inputs.
3. The permanent compatibility class ran against the original implementation: 538/538 passed.
4. GeoTools, its EPSG database, and its repository were removed again.
5. The exact same class and oracle ran against the replacement: 538/538 passed.

The oracle contains 336 geodesic inputs and 200 EPSG:3857 inputs. It covers millimeter-scale local steps, randomized local and global pairs, both hemispheres, poles, dateline crossings, exact and near antipodes, projection boundaries, and forward/reverse distance symmetry. Distance comparison uses a one-millimeter floor and 1e-10 relative tolerance; projection comparison uses a one-micrometer floor and 1e-13 relative tolerance.

The first replacement run completed the compatibility class in 1.016 seconds versus 2.920 seconds for GeoTools. This is not treated as a formal benchmark, but it supports the real-workload JFR result. The direct dependency audit contains GeographicLib 2.1 and no GeoTools artifact.

The command results are summarized in [geotools-compatibility-evidence.txt](geotools-compatibility-evidence.txt).

## Large private-file comparison

The ten largest files from the local private corpus were selected without recording their names or content. Together they exercised 305,297 raw points and produced the same ten successful tracks and the same derived row counts before and after the change.

| Measurement | Original | Optimized |
|---|---:|---:|
| App cgroup limit | 1,536 MiB | 512 MiB |
| App peak | 1,411.9 MiB | 512 MiB hard limit |
| JVM maximum heap | 768 MiB | 160 MiB |
| Runtime | 205.7 s | 246.4 s |
| Swap / OOM | none / none | none / none |
| Weighted JFR allocation rate | 95.02 GiB/min | 8.51 GiB/min |

The weighted allocation rate fell about 91%. The optimized JFR no longer showed GeoTools, GeographicLib, or stop-detector arrays among the dominant allocation sites. Profiling itself retained about 30 MiB of JFR tracing memory after recording stopped, so profiler RSS was not used as the production limit.

Native Memory Tracking on the 512 MiB run reported about 451 MiB committed in total: 160 MiB heap, 122 MiB metaspace, 42 MiB code, 42 MiB GC, 28 MiB symbols, and smaller categories. This explains why a 160 MiB heap can still produce roughly 500 MiB process RSS.

## Public demo workload

All 15,000 public demo GPX files indexed under a 768 MiB app limit with swap disabled. Import completed in 379.1 seconds. The final domain state was 10,042 unique tracks, 4,954 excluded tracks, and four duplicates. The app peak was 783,589,376 bytes (747.3 MiB), with no OOM.

PostgreSQL at 320 MiB was not safe: a backend was OOM-killed during post-index correlation. Raising the database limit to 384 MiB produced no further OOM while the workload settled. The validated floor is therefore 384 MiB; the configured limit is 512 MiB. The 20 percent threshold is 460.8 MiB and is rounded up to a conventional allocation.

An earlier conservative run of the updated demo photo generator completed 2,000 photos under a 1,280 MiB limit and recorded a 1,078.3 MiB cgroup peak. A current-release follow-up on 2026-08-25 started the full 15,000-GPX and 2,000-photo first-start path at a hard 1,024 MiB limit. It reached several thousand completed GPX imports with a 782.4 MiB peak, no cgroup limit hits, no OOM, and no restart before the run was stopped early. The current demo profile therefore uses 1,024 MiB; complete first-start validation remains a deployment follow-up.

## Media helpers

The standard 640 MiB app container successfully processed:

- a synthetic 6,000 x 4,000 JPEG resize;
- a thumbnail from an eight-second synthetic 4K video;
- a 4K-to-720p HLS transcode.

Peak app cgroup use was 639.9, 575.3, and 639.6 MiB respectively. All outputs were valid, media indexing reached 2/2, and no run swapped or recorded an OOM. The tight peaks establish a 640 MiB standard-app floor even though GPX-only import can run at 512 MiB. The NAS app limit is 768 MiB after adding 20 percent headroom. The home profile keeps 1,024 MiB to provide a 320 MiB Java heap and extra media-processing margin.

## Validated floors and configured margins

| Container | Validated floor | Configured limit | Swap | Reason |
|---|---:|---:|---:|---|
| Standard app | 640 MiB | 768 MiB NAS / 1,024 MiB home | disabled | Passed large GPX, live/offline indexer, image, and 4K video checks |
| PostgreSQL | 384 MiB | 512 MiB | disabled | Passed indexer and public post-import work; 320 MiB failed |
| Standard total | 1,024 MiB | 1,280 MiB NAS / 1,536 MiB home | disabled | App plus database only; sidecars, Docker, and the OS are additional |
| Demo app | 1,280 MiB earlier full run | 1,024 MiB current target | disabled | Updated photo path; partial current-release first-start check passed without a limit hit |

The standard app and PostgreSQL values are at least 20% above their validated floors and use conventional allocations. PostgreSQL's 20 percent threshold is 460.8 MiB and is rounded up to 512 MiB. The 1,024 MiB demo target replaces the earlier conservative limit and still needs a complete first-start check on the demo instance.

## Verification

### Current tree after review

The current working tree was verified again on 2026-08-25 after the final source changes and the generated-image busy-response correction:

- Java 25 server regression: 692 run; 691 passed and one FFmpeg-gated test skipped. This includes the 598-test GPX parsing, outlier, stop-detection, projection, and compatibility set plus 94 focused GPX store, indexer, media, and controller tests.
- The media controller regression checks that a saturated image-processing limiter returns `503`, `Cache-Control: no-store`, and no `ETag` or `Last-Modified` validators.
- Production Docker `app-builder` reactor: passed. It regenerated the OpenAPI client, built the client for production, compiled all server and test sources, and packaged the server.
- Both Compose configurations rendered successfully.
- Server dependency audit: GeographicLib 2.1 present, GeoTools absent.
- `git diff --check`: passed.

Commands and result summaries are in [post-review-verification.txt](post-review-verification.txt).

### Original memory-workload evidence

The following results were captured on 2026-08-24 as part of the profiling and constrained-container workload. They support the memory findings above. They predate the post-review cache-response correction, which does not change GPX calculations, indexing, or container resource settings.

- GeoTools 35.1 oracle against the temporarily restored original methods: 538 passed.
- Identical oracle against the replacement: 538 passed.
- Focused GPX/indexer/media server tests: 87 passed.
- Full video service class: 13 run; 12 passed and one skipped.
- Client freshness suites: 24 passed.
- Live and offline two-pass indexer regression: passed at 640 MiB app and 384 MiB database limits.

The result is a bounded container configuration, not a claim that Java retains hundreds of megabytes of GPX. The large fixed framework/runtime footprint remains, but GPX allocation churn and unnecessary runtime dependencies were materially reduced.
